package at.kidstune.notification;

import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.web.AvatarHelper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailNotificationServiceTest {

    private EmailNotificationService service;

    private JavaMailSender         mailSender;
    private ContentRequestRepository requestRepository;
    private ProfileRepository         profileRepository;
    private FamilyRepository          familyRepository;
    private TemplateEngine            templateEngine;

    @BeforeEach
    void setUp() {
        mailSender        = mock(JavaMailSender.class);
        requestRepository = mock(ContentRequestRepository.class);
        profileRepository = mock(ProfileRepository.class);
        familyRepository  = mock(FamilyRepository.class);
        templateEngine    = mock(TemplateEngine.class);
        AvatarHelper avatarHelper = new AvatarHelper();

        service = new EmailNotificationService(
                requestRepository, profileRepository, familyRepository,
                templateEngine, avatarHelper);
        service.setMailSender(mailSender);

        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>test</html>");
    }

    // ── parseEmails ───────────────────────────────────────────────────────────

    @Test
    void parseEmails_returns_empty_for_null() {
        assertThat(EmailNotificationService.parseEmails(null)).isEmpty();
    }

    @Test
    void parseEmails_returns_empty_for_blank() {
        assertThat(EmailNotificationService.parseEmails("  ")).isEmpty();
    }

    @Test
    void parseEmails_splits_on_comma() {
        assertThat(EmailNotificationService.parseEmails("a@x.de,b@x.de"))
                .containsExactly("a@x.de", "b@x.de");
    }

    @Test
    void parseEmails_splits_on_semicolon_and_whitespace() {
        assertThat(EmailNotificationService.parseEmails("a@x.de; b@x.de  c@x.de"))
                .containsExactly("a@x.de", "b@x.de", "c@x.de");
    }

    // ── sendRequestNotification ───────────────────────────────────────────────

    @Test
    void sendRequestNotification_skips_when_no_notification_emails() {
        ContentRequest request = requestWithToken("req-1", "profile-1", "Bibi & Tina");
        ChildProfile profile   = profileWith("profile-1", "Luna", "family-1");
        Family family          = familyWith("family-1", null); // no notification_emails

        when(profileRepository.findById("profile-1")).thenReturn(Optional.of(profile));
        when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));

        service.sendRequestNotification(request);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendRequestNotification_sends_to_all_configured_recipients() throws Exception {
        ContentRequest request = requestWithToken("req-1", "profile-1", "Bibi & Tina");
        ChildProfile profile   = profileWith("profile-1", "Luna", "family-1");
        Family family          = familyWith("family-1", "mama@example.de,papa@example.de");

        when(profileRepository.findById("profile-1")).thenReturn(Optional.of(profile));
        when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));

        service.sendRequestNotification(request);

        verify(mailSender).send(any(MimeMessage.class));
        // Template was processed with correct request data
        verify(templateEngine).process(eq("email/request-notification"), any(Context.class));
    }

    @Test
    void sendRequestNotification_builds_correct_approve_url() {
        // Capture the Thymeleaf context to verify approveUrl
        ContentRequest request = requestWithToken("req-1", "profile-1", "Bibi & Tina");
        request.setApproveToken("test-token-abc");
        ChildProfile profile = profileWith("profile-1", "Luna", "family-1");
        Family family        = familyWith("family-1", "mama@example.de");

        when(profileRepository.findById("profile-1")).thenReturn(Optional.of(profile));
        when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));
        when(templateEngine.process(anyString(), any(Context.class))).thenAnswer(inv -> {
            Context ctx = inv.getArgument(1);
            assertThat(ctx.getVariable("approveUrl").toString())
                    .endsWith("/web/approve/test-token-abc");
            assertThat(ctx.getVariable("childName")).isEqualTo("Luna");
            assertThat(ctx.getVariable("title")).isEqualTo("Bibi & Tina");
            return "<html>ok</html>";
        });

        service.sendRequestNotification(request);
    }

    @Test
    void sendRequestNotification_skips_when_profile_not_found() {
        ContentRequest request = requestWithToken("req-1", "missing-profile", "Title");
        when(profileRepository.findById("missing-profile")).thenReturn(Optional.empty());

        service.sendRequestNotification(request); // must not throw

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendRequestNotification_does_not_throw_when_mail_fails() {
        ContentRequest request = requestWithToken("req-1", "profile-1", "Title");
        ChildProfile profile   = profileWith("profile-1", "Luna", "family-1");
        Family family          = familyWith("family-1", "mama@example.de");

        when(profileRepository.findById("profile-1")).thenReturn(Optional.of(profile));
        when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP down"));

        service.sendRequestNotification(request); // must not propagate the exception
    }

    // ── sendRequestNotification without mailSender ────────────────────────────

    @Test
    void sendRequestNotification_logs_error_when_mail_not_configured() {
        // Create service with no mailSender
        EmailNotificationService noMail = new EmailNotificationService(
                requestRepository, profileRepository, familyRepository,
                templateEngine, new AvatarHelper());
        // mailSender intentionally NOT injected

        ContentRequest request = requestWithToken("req-1", "profile-1", "Title");
        noMail.sendRequestNotification(request); // must not throw, must not send

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ContentRequest requestWithToken(String id, String profileId, String title) {
        ContentRequest r = new ContentRequest();
        r.setProfileId(profileId);
        r.setTitle(title);
        r.setArtistName("Test Artist");
        r.setApproveToken(id + "-token");
        // reflectively set id to avoid @PrePersist side-effects
        try {
            var f = ContentRequest.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, id);
            var s = ContentRequest.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, at.kidstune.requests.ContentRequestStatus.PENDING);
        } catch (Exception e) { throw new RuntimeException(e); }
        return r;
    }

    private static ChildProfile profileWith(String id, String name, String familyId) {
        ChildProfile p = new ChildProfile();
        p.setFamilyId(familyId);
        p.setName(name);
        p.setAvatarIcon(AvatarIcon.BEAR);
        p.setAvatarColor(AvatarColor.BLUE);
        try {
            var f = ChildProfile.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return p;
    }

    private static Family familyWith(String id, String notificationEmails) {
        Family f = new Family();
        f.setNotificationEmails(notificationEmails);
        try {
            var field = Family.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(f, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return f;
    }
}
