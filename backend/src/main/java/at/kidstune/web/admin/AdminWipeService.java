package at.kidstune.web.admin;

import at.kidstune.auth.PairingCodeRepository;
import at.kidstune.content.ContentRepository;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.family.FamilyRepository;
import at.kidstune.favorites.FavoriteRepository;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.push.PushSubscriptionRepository;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.resolver.ResolvedAlbumRepository;
import at.kidstune.resolver.ResolvedTrackRepository;
import at.kidstune.sync.DeletionLogRepository;
import at.kidstune.web.RememberMeTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wipes ALL data from the database in FK-safe order.
 * Used exclusively by the admin Danger Zone endpoint.
 */
@Service
public class AdminWipeService {

    private static final Logger log = LoggerFactory.getLogger(AdminWipeService.class);

    private final ContentRequestRepository  contentRequestRepository;
    private final FavoriteRepository        favoriteRepository;
    private final DeletionLogRepository     deletionLogRepository;
    private final ResolvedTrackRepository   resolvedTrackRepository;
    private final ResolvedAlbumRepository   resolvedAlbumRepository;
    private final ContentRepository         contentRepository;
    private final PairedDeviceRepository    pairedDeviceRepository;
    private final ProfileRepository         profileRepository;
    private final PairingCodeRepository     pairingCodeRepository;
    private final RememberMeTokenRepository rememberMeTokenRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final FamilyRepository          familyRepository;

    public AdminWipeService(ContentRequestRepository contentRequestRepository,
                            FavoriteRepository favoriteRepository,
                            DeletionLogRepository deletionLogRepository,
                            ResolvedTrackRepository resolvedTrackRepository,
                            ResolvedAlbumRepository resolvedAlbumRepository,
                            ContentRepository contentRepository,
                            PairedDeviceRepository pairedDeviceRepository,
                            ProfileRepository profileRepository,
                            PairingCodeRepository pairingCodeRepository,
                            RememberMeTokenRepository rememberMeTokenRepository,
                            PushSubscriptionRepository pushSubscriptionRepository,
                            FamilyRepository familyRepository) {
        this.contentRequestRepository  = contentRequestRepository;
        this.favoriteRepository        = favoriteRepository;
        this.deletionLogRepository     = deletionLogRepository;
        this.resolvedTrackRepository   = resolvedTrackRepository;
        this.resolvedAlbumRepository   = resolvedAlbumRepository;
        this.contentRepository         = contentRepository;
        this.pairedDeviceRepository    = pairedDeviceRepository;
        this.profileRepository         = profileRepository;
        this.pairingCodeRepository     = pairingCodeRepository;
        this.rememberMeTokenRepository = rememberMeTokenRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.familyRepository          = familyRepository;
    }

    /**
     * Deletes all data rows in FK-safe order.
     * Does NOT drop schema or Liquibase changelog history – data rows only.
     */
    @Transactional
    public void wipeAllData() {
        log.warn("ADMIN WIPE: Deleting ALL data from database");

        contentRequestRepository.deleteAll();
        favoriteRepository.deleteAll();
        deletionLogRepository.deleteAll();
        resolvedTrackRepository.deleteAll();
        resolvedAlbumRepository.deleteAll();
        contentRepository.deleteAll();
        pairedDeviceRepository.deleteAll();
        profileRepository.deleteAll();
        pairingCodeRepository.deleteAll();
        rememberMeTokenRepository.deleteAll();
        pushSubscriptionRepository.deleteAll();
        familyRepository.deleteAll();

        log.warn("ADMIN WIPE: Complete");
    }
}
