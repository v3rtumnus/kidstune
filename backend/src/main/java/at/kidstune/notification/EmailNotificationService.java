package at.kidstune.notification;

import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.web.AvatarHelper;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private JavaMailSender mailSender; // null when spring.mail.host not configured

    private final ContentRequestRepository requestRepository;
    private final ProfileRepository        profileRepository;
    private final FamilyRepository         familyRepository;
    private final TemplateEngine           templateEngine;
    private final AvatarHelper             avatarHelper;

    @Value("${kidstune.base-url}")
    private String baseUrl;

    public EmailNotificationService(ContentRequestRepository requestRepository,
                                     ProfileRepository profileRepository,
                                     FamilyRepository familyRepository,
                                     TemplateEngine templateEngine,
                                     AvatarHelper avatarHelper) {
        this.requestRepository = requestRepository;
        this.profileRepository = profileRepository;
        this.familyRepository  = familyRepository;
        this.templateEngine    = templateEngine;
        this.avatarHelper      = avatarHelper;
    }

    @Autowired(required = false)
    public void setMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostConstruct
    void warnIfMailNotConfigured() {
        if (mailSender == null) {
            log.error("Spring Mail is not configured (SPRING_MAIL_HOST not set). " +
                      "Email notifications will NOT be sent.");
        }
    }

    // ── Request notification (called async from ContentRequestService) ──────────

    @Async
    public void sendRequestNotification(ContentRequest request) {
        if (mailSender == null) {
            log.error("Cannot send request notification for request {}: mail not configured.",
                      request.getId());
            return;
        }
        try {
            ChildProfile profile = profileRepository.findById(request.getProfileId()).orElse(null);
            if (profile == null) {
                log.warn("Profile {} not found – skipping notification for request {}",
                         request.getProfileId(), request.getId());
                return;
            }
            Family family = familyRepository.findById(profile.getFamilyId()).orElse(null);
            if (family == null) {
                log.warn("Family {} not found – skipping notification for request {}",
                         profile.getFamilyId(), request.getId());
                return;
            }
            List<String> recipients = parseEmails(family.getNotificationEmails());
            if (recipients.isEmpty()) {
                log.warn("No notification_emails configured for family {} – request {} not notified.",
                         family.getId(), request.getId());
                return;
            }

            Context ctx = new Context();
            ctx.setVariable("childName",    profile.getName());
            ctx.setVariable("childEmoji",   avatarHelper.emoji(profile.getAvatarIcon()));
            ctx.setVariable("childColor",   avatarHelper.cssColor(profile.getAvatarColor()));
            ctx.setVariable("title",        request.getTitle());
            ctx.setVariable("artistName",   request.getArtistName());
            ctx.setVariable("imageUrl",     request.getImageUrl());
            ctx.setVariable("approveUrl",   baseUrl + "/web/approve/" + request.getApproveToken());
            ctx.setVariable("dashboardUrl", baseUrl + "/web/requests");

            String body    = templateEngine.process("email/request-notification", ctx);
            String subject = "\uD83C\uDFB5 " + profile.getName()
                           + " m\u00F6chte \u201E" + request.getTitle() + "\u201C h\u00F6ren";

            sendHtml(recipients, subject, body);
            log.debug("Request notification sent for request {} to {} recipient(s).",
                      request.getId(), recipients.size());

        } catch (Exception e) {
            log.error("Failed to send request notification for request {}: {}",
                      request.getId(), e.getMessage(), e);
        }
    }

    // ── Daily digest (scheduled at 19:00) ────────────────────────────────────

    @Scheduled(cron = "0 0 19 * * *")
    public void sendDailyDigest() {
        if (mailSender == null) {
            log.error("Cannot send daily digest: mail not configured.");
            return;
        }
        Instant cutoff = Instant.now().minus(4, ChronoUnit.HOURS);
        List<ContentRequest> pending = requestRepository.findPendingOlderThanWithoutDigest(cutoff);
        if (pending.isEmpty()) return;

        Map<String, List<ContentRequest>> byFamily = groupByFamily(pending);

        for (Map.Entry<String, List<ContentRequest>> entry : byFamily.entrySet()) {
            String familyId              = entry.getKey();
            List<ContentRequest> requests = entry.getValue();
            try {
                Family family = familyRepository.findById(familyId).orElse(null);
                if (family == null) continue;

                List<String> recipients = parseEmails(family.getNotificationEmails());
                if (recipients.isEmpty()) continue;

                Map<String, ChildProfile> profileMap = profileRepository.findByFamilyId(familyId)
                        .stream().collect(Collectors.toMap(ChildProfile::getId, p -> p));

                List<DigestEntry> entries = requests.stream()
                        .map(r -> {
                            ChildProfile p = profileMap.get(r.getProfileId());
                            String emoji     = p != null ? avatarHelper.emoji(p.getAvatarIcon()) : "?";
                            String childName = p != null ? p.getName() : "Unknown";
                            return new DigestEntry(
                                    r,
                                    childName,
                                    emoji,
                                    baseUrl + "/web/approve/" + r.getApproveToken());
                        })
                        .toList();

                Context ctx = new Context();
                ctx.setVariable("entries",      entries);
                ctx.setVariable("count",        requests.size());
                ctx.setVariable("dashboardUrl", baseUrl + "/web/requests");

                String body    = templateEngine.process("email/daily-digest", ctx);
                String subject = "\uD83C\uDFB5 " + requests.size() + " Musikwunsch"
                               + (requests.size() == 1 ? "" : "w\u00FCnsche")
                               + " warten auf dich";

                sendHtml(recipients, subject, body);

                Instant now = Instant.now();
                for (ContentRequest r : requests) {
                    r.setDigestSentAt(now);
                    requestRepository.save(r);
                }
                log.debug("Daily digest sent for family {} ({} request(s)) to {} recipient(s).",
                          familyId, requests.size(), recipients.size());

            } catch (Exception e) {
                log.error("Failed to send daily digest for family {}: {}", familyId, e.getMessage(), e);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendHtml(List<String> to, String subject, String htmlBody) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
        helper.setTo(to.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(msg);
    }

    private Map<String, List<ContentRequest>> groupByFamily(List<ContentRequest> requests) {
        Map<String, String> profileToFamily = requests.stream()
                .map(ContentRequest::getProfileId)
                .distinct()
                .flatMap(pid -> profileRepository.findById(pid).stream())
                .collect(Collectors.toMap(ChildProfile::getId, ChildProfile::getFamilyId));

        return requests.stream()
                .filter(r -> profileToFamily.containsKey(r.getProfileId()))
                .collect(Collectors.groupingBy(r -> profileToFamily.get(r.getProfileId())));
    }

    static final int MAX_NOTIFICATION_EMAILS = 10;

    /**
     * Parses a comma/semicolon/whitespace-separated string of email addresses.
     * Capped at {@value #MAX_NOTIFICATION_EMAILS} addresses to prevent abuse.
     */
    static List<String> parseEmails(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .limit(MAX_NOTIFICATION_EMAILS)
                .toList();
    }

    public record DigestEntry(
            ContentRequest request,
            String         childName,
            String         emoji,
            String         approveUrl
    ) {}
}
