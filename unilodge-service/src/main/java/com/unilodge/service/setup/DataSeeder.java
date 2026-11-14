package com.unilodge.service.setup;

import com.unilodge.common.enums.FurnishedStatus;
import com.unilodge.common.enums.ListingStatus;
import com.unilodge.common.enums.ModerationStatus;
import com.unilodge.common.enums.PropertyType;
import com.unilodge.common.enums.UserRole;
import com.unilodge.common.enums.UserStatus;
import com.unilodge.common.util.GeoUtils;
import com.unilodge.persistence.entity.Comment;
import com.unilodge.persistence.entity.Property;
import com.unilodge.persistence.entity.PropertyImage;
import com.unilodge.persistence.entity.SavedProperty;
import com.unilodge.persistence.entity.StudentPost;
import com.unilodge.persistence.entity.University;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.CommentRepository;
import com.unilodge.persistence.repository.PropertyImageRepository;
import com.unilodge.persistence.repository.PropertyRepository;
import com.unilodge.persistence.repository.SavedPropertyRepository;
import com.unilodge.persistence.repository.StudentPostRepository;
import com.unilodge.persistence.repository.UniversityRepository;
import com.unilodge.persistence.repository.UserRepository;
import com.unilodge.service.config.AppProperties;
import com.unilodge.service.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * First-run bootstrap: seeds the three original administrators and, when
 * enabled, a realistic set of demo accounts, listings, posts and comments.
 *
 * <p>Demo seeding is idempotent. It adds any missing sample listings and
 * student-board threads, and it regenerates sample photos that disappeared
 * with an ephemeral container filesystem (the Render free plan resets the
 * filesystem whenever the service restarts).</p>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String DEMO_IMAGE_PREFIX = "unilodge-demo-";
    private static final String DEMO_PASSWORD = "Demo@1234";

    private static final Color[][] PALETTES = {
            {new Color(0x2E6B52), new Color(0xA8C9A0)},
            {new Color(0x1F4E5F), new Color(0x9FC6C9)},
            {new Color(0x6B4F2E), new Color(0xD5C0A0)},
            {new Color(0x3F5A3A), new Color(0xC2D6A4)},
            {new Color(0x4A3F63), new Color(0xB9AED6)}
    };

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository imageRepository;
    private final StudentPostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SavedPropertyRepository savedPropertyRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public DataSeeder(AppProperties properties,
                      UserRepository userRepository,
                      UniversityRepository universityRepository,
                      PropertyRepository propertyRepository,
                      PropertyImageRepository imageRepository,
                      StudentPostRepository postRepository,
                      CommentRepository commentRepository,
                      SavedPropertyRepository savedPropertyRepository,
                      PasswordEncoder passwordEncoder,
                      FileStorageService fileStorageService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.universityRepository = universityRepository;
        this.propertyRepository = propertyRepository;
        this.imageRepository = imageRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.savedPropertyRepository = savedPropertyRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedOriginalAdmins();
        if (properties.getBootstrap().isDemoData()) {
            try {
                seedDemoData();
            } catch (Exception exception) {
                log.warn("Demo data seeding skipped: {}", exception.getMessage(), exception);
            }
        }
    }

    private void seedOriginalAdmins() {
        for (String rawEmail : properties.getBootstrap().getAdminEmails()) {
            String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
            if (email.isEmpty() || userRepository.existsByEmailIgnoreCase(email)) {
                continue;
            }
            User admin = new User();
            admin.setEmail(email);
            admin.setFullName("UniLodge Administrator");
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setEmailVerified(false);
            admin.setOriginalAdmin(true);
            userRepository.save(admin);
            log.info("Original administrator seeded: {} (complete setup at /admin-setup.html)", email);
        }
    }

    private void seedDemoData() {
        University palm = universityRepository.findByNameIgnoreCase("Palm University College")
                .orElseGet(() -> universityRepository.findByActiveTrueOrderByNameAsc().stream()
                        .findFirst()
                        .orElse(null));
        if (palm == null) {
            log.warn("Demo data needs at least one active university; skipping.");
            return;
        }

        String demoPassword = passwordEncoder.encode(DEMO_PASSWORD);

        User student = user("kwame.student@unilodge.test", "Kwame Boateng", UserRole.STUDENT,
                UserStatus.ACTIVE, "+233 24 555 0102", palm, null, demoPassword);
        User studentTwo = user("abena.student@unilodge.test", "Abena Sarpong", UserRole.STUDENT,
                UserStatus.ACTIVE, "+233 24 555 0104", palm, null, demoPassword);
        User studentThree = user("yaw.student@unilodge.test", "Yaw Danquah", UserRole.STUDENT,
                UserStatus.ACTIVE, "+233 24 555 0105", palm, null, demoPassword);
        User landlord = user("ama.landlord@unilodge.test", "Ama Mensah", UserRole.LANDLORD,
                UserStatus.ACTIVE, "+233 24 555 0101", null, "Ama Student Homes", demoPassword);
        User landlordTwo = user("kofi.landlord@unilodge.test", "Kofi Owusu", UserRole.LANDLORD,
                UserStatus.PENDING_APPROVAL, "+233 20 555 0103", null, "Owusu Properties", demoPassword);

        int created = ensureDemoListings(palm, landlord, landlordTwo);
        repairMissingDemoImages();

        if (postRepository.countByStatus(ModerationStatus.VISIBLE) == 0) {
            seedStudentBoard(student, studentTwo, studentThree, landlord, palm);
        }

        if (created > 0) {
            List<Property> saved = propertyRepository.findAll().stream()
                    .filter(property -> property.getLandlord().getId().equals(landlord.getId()))
                    .filter(property -> property.getStatus() == ListingStatus.APPROVED)
                    .limit(2)
                    .toList();
            for (Property property : saved) {
                if (!savedPropertyRepository.existsByUserIdAndPropertyId(student.getId(), property.getId())) {
                    save(student, property);
                }
            }
        }

        log.info("""

                Demo data ready
                  Student  : kwame.student@unilodge.test / Demo@1234
                  Student  : abena.student@unilodge.test / Demo@1234
                  Student  : yaw.student@unilodge.test / Demo@1234
                  Landlord : ama.landlord@unilodge.test / Demo@1234 (approved)
                  Landlord : kofi.landlord@unilodge.test / Demo@1234 (awaiting approval)
                """);
    }

    /**
     * Creates any missing sample listings. Titles act as the natural key, so
     * restarting the service or upgrading an earlier demo database does not
     * duplicate rows.
     */
    private int ensureDemoListings(University palm, User landlord, User landlordTwo) {
        Set<String> existingTitles = new HashSet<>();
        for (Property property : propertyRepository.findAll()) {
            if (property.getLandlord() != null && isDemoLandlord(property.getLandlord())) {
                existingTitles.add(property.getTitle());
            }
        }

        Instant base = Instant.now();
        int created = 0;
        List<DemoListing> listings = demoListings();
        for (int index = 0; index < listings.size(); index++) {
            DemoListing listing = listings.get(index);
            if (existingTitles.contains(listing.title())) {
                continue;
            }
            User owner = index % 2 == 0 ? landlord : landlordTwo;
            property(palm, owner, listing, index, base.minusSeconds(index * 3600L));
            created++;
        }
        return created;
    }

    private boolean isDemoLandlord(User user) {
        return user.getEmail() != null && user.getEmail().toLowerCase().endsWith("@unilodge.test");
    }

    private void seedStudentBoard(User student, User studentTwo, User studentThree,
                                  User landlord, University palm) {
        StudentPost postOne = post(student, palm, "Looking for a roommate to share a two-bedroom",
                "I am a second-year student at Palm University looking for one calm, tidy roommate to "
                        + "share a two-bedroom apartment close to campus. I cook often, keep shared spaces "
                        + "clean and I am happy to split rent and utilities equally.",
                "Shai Hills / near campus", "600", 1);
        StudentPost postTwo = post(studentTwo, palm, "Female student looking for a furnished room",
                "Hello! I am starting at Palm University this semester and looking for a furnished room "
                        + "with reliable water and Wi-Fi. My budget is around GH₵600 a month and I would "
                        + "like somewhere within walking distance of campus.",
                "Within walking distance of Palm University", "600", 0);
        StudentPost postThree = post(studentThree, palm, "Hostel or single room around Shai Hills?",
                "I am moving to Shai Hills in January and comparing hostel rooms with single rooms in "
                        + "shared houses. If you have stayed in either, which one worked better for you? "
                        + "I care most about steady water and a quiet place to study at night.",
                "Shai Hills", "700", 0);
        StudentPost postFour = post(student, palm, "Two calm roommates wanted for a shared apartment",
                "Three of us are hoping to take a two-bedroom apartment near the Palm gate. We need two "
                        + "more students who are comfortable sharing a room and splitting the monthly rent. "
                        + "No smoking please, and we keep the common areas tidy.",
                "Near Palm University gate", "500", 2);
        StudentPost postFive = post(studentTwo, palm, "Is GH₵700 enough for a studio near campus?",
                "I have been saving for a self-contained studio and my ceiling is GH₵700 a month. I have "
                        + "seen a few places online but I am not sure whether that budget is realistic once "
                        + "water and electricity are added. Any recent experiences would help.",
                "Shai Hills / Oyibi", "700", 0);
        StudentPost postSix = post(studentThree, palm, "Moving in January, need reliable water storage",
                "Looking for a single room or hostel space with a water tank and a prepaid meter. I will "
                        + "be commuting to Palm University every day, so anywhere within about 20 minutes "
                        + "of campus would be ideal. My move-in date is flexible by a week or two.",
                "Shai Hills, Oyibi or Amasaman", "650", 0);
        StudentPost postSeven = post(student, palm, "Roommate needed to split rent near Palm gate",
                "A friend and I found a two-bedroom place about 10 minutes from campus. One room is still "
                        + "open and we would like another student to join us. Rent and utilities are split "
                        + "three ways, and the landlord is happy to meet before you decide.",
                "Palm University gate area", "550", 1);
        StudentPost postEight = post(studentTwo, palm, "Anyone commuting from Oyibi? Looking to share",
                "I am considering a place in Oyibi because the rent is lower, but I do not want to travel "
                        + "alone every day. If you are commuting to Palm University from Oyibi, I would love "
                        + "to compare notes on trotro routes, costs and travel times.",
                "Oyibi", "450", 0);

        comment(postOne, studentTwo, "I am also looking around Shai Hills. Sending you a message!");
        comment(postOne, landlord, "I have a two-bedroom apartment that is perfect for sharing, "
                + "see my listing 'Two-bedroom apartment for sharing'.");
        comment(postTwo, student, "Update: I have viewed two places and would love more options "
                + "with utilities included.");
        comment(postThree, studentTwo, "I stayed in a hostel last semester. The steady water was the "
                + "best part, but the shared study room got busy during exams.");
        comment(postFour, studentThree, "This sounds like a good fit for me. Is the room still open?");
        comment(postFive, landlord, "GH₵700 is possible for a single room, but a self-contained studio "
                + "usually starts a little higher once utilities are added.");
        comment(postSix, student, "Try the houses behind the market. A few of them have new water tanks.");
        comment(postSeven, studentTwo, "I may be interested. How far is the walk to the main gate?");
        comment(postEight, studentThree, "I commute from Oyibi three days a week. The morning trotro is "
                + "busy but manageable if you leave before 6:30.");
    }

    private record DemoListing(
            String title,
            String description,
            PropertyType type,
            String rent,
            FurnishedStatus furnished,
            int bedrooms,
            int bathrooms,
            String address,
            String city,
            double latitude,
            double longitude,
            int moveInDays,
            Set<String> amenities,
            boolean pending) {
    }

    private List<DemoListing> demoListings() {
        return List.of(
                new DemoListing("Sunny single room near Palm University",
                        "A bright, furnished single room a short walk from campus. The compound is fenced, has "
                                + "a shared kitchen and reliable water storage. Perfect for a first-year student "
                                + "who wants a calm, secure place to study.",
                        PropertyType.SINGLE_ROOM, "450", FurnishedStatus.FURNISHED, 1, 1,
                        "12 Palm Estate Road", "Shai Hills", 5.8955, 0.0410, 45,
                        Set.of("Wi-Fi", "Water storage", "Fenced compound", "Shared kitchen"), false),
                new DemoListing("Modern self-contained studio in Shai Hills",
                        "Newly finished self-contained studio with its own kitchenette and bathroom. Tiled "
                                + "throughout, prepaid meter, and space for a study desk. Great natural light "
                                + "during the day and a quiet neighbourhood at night.",
                        PropertyType.STUDIO, "780", FurnishedStatus.SEMI_FURNISHED, 1, 1,
                        "5 Baatsona Close", "Shai Hills", 5.8890, 0.0330, 45,
                        Set.of("Prepaid meter", "Kitchenette", "Private bathroom", "Parking"), false),
                new DemoListing("Two-bedroom apartment for sharing",
                        "Spacious two-bedroom apartment that works beautifully for two students sharing. "
                                + "Large living area, fitted kitchen, and a balcony overlooking a quiet street. "
                                + "Rent can be split between roommates.",
                        PropertyType.APARTMENT, "1100", FurnishedStatus.FURNISHED, 2, 1,
                        "22 Green Valley Lane", "Shai Hills", 5.9010, 0.0270, 60,
                        Set.of("Balcony", "Fitted kitchen", "Wi-Fi ready", "Gated"), false),
                new DemoListing("Budget room in a friendly shared house",
                        "An affordable room in a friendly shared house with two other students. Utilities are "
                                + "shared fairly and the landlord lives nearby, so repairs are handled quickly. "
                                + "Bring your own mattress if you prefer.",
                        PropertyType.SINGLE_ROOM, "520", FurnishedStatus.UNFURNISHED, 1, 1,
                        "8 Market Road", "Shai Hills", 5.8840, 0.0450, 90,
                        Set.of("Shared bathroom", "Water tank", "Close to trotro stop"), false),
                new DemoListing("Quiet hostel room with utilities included",
                        "A calm hostel room with electricity and water included in the rent. There is a "
                                + "shared study room on the ground floor and a caretaker on site every day. "
                                + "Ideal if you want one predictable monthly cost.",
                        PropertyType.HOSTEL, "650", FurnishedStatus.FURNISHED, 1, 1,
                        "Hostel Block C, Palm Junction", "Shai Hills", 5.8875, 0.0295, 30,
                        Set.of("Utilities included", "Study room", "Caretaker", "Wi-Fi"), false),
                new DemoListing("Cosy single room with prepaid meter",
                        "A neat single room in a small compound of four students. The room has a prepaid "
                                + "meter, a ceiling fan and a private entrance. There is a shared washing area "
                                + "and a covered space to dry clothes during the rainy season.",
                        PropertyType.SINGLE_ROOM, "480", FurnishedStatus.SEMI_FURNISHED, 1, 1,
                        "3 Cashew Street", "Shai Hills", 5.8975, 0.0445, 20,
                        Set.of("Prepaid meter", "Ceiling fan", "Private entrance"), false),
                new DemoListing("Furnished studio close to the campus gate",
                        "A compact furnished studio about eight minutes from the Palm University gate. It "
                                + "comes with a bed, wardrobe, small fridge and a study table. The compound is "
                                + "gated and has a shared water tank.",
                        PropertyType.STUDIO, "850", FurnishedStatus.FURNISHED, 1, 1,
                        "14 Gateview Close", "Shai Hills", 5.8920, 0.0395, 15,
                        Set.of("Furnished", "Fridge", "Gated", "Water tank"), false),
                new DemoListing("Shared two-bedroom with a study corner",
                        "Two students are already living here and one room has opened up. The apartment has "
                                + "a study corner beside the window, a fitted kitchen and a small balcony. "
                                + "Internet is already installed and split three ways.",
                        PropertyType.APARTMENT, "950", FurnishedStatus.FURNISHED, 2, 1,
                        "31 Unity Crescent", "Shai Hills", 5.9060, 0.0315, 40,
                        Set.of("Internet installed", "Study corner", "Balcony", "Fitted kitchen"), false),
                new DemoListing("Ensuite room in a gated student house",
                        "A private ensuite room in a modern student house with four other rooms. There is "
                                + "a shared kitchen, a laundry area and security at the gate. Rent includes "
                                + "water, and electricity is on a prepaid meter.",
                        PropertyType.SINGLE_ROOM, "700", FurnishedStatus.FURNISHED, 1, 1,
                        "7 Palm Grove", "Shai Hills", 5.8930, 0.0470, 25,
                        Set.of("Ensuite", "Security", "Laundry area", "Water included"), false),
                new DemoListing("Chamber and hall with private kitchen",
                        "A well-kept chamber and hall with a separate kitchen and bathroom. It suits a "
                                + "student who wants more space than a single room without paying for a full "
                                + "two-bedroom apartment. The compound is quiet and paved.",
                        PropertyType.APARTMENT, "1000", FurnishedStatus.SEMI_FURNISHED, 1, 1,
                        "19 Cedar Avenue", "Shai Hills", 5.9025, 0.0235, 55,
                        Set.of("Private kitchen", "Paved compound", "Tiled floors"), false),
                new DemoListing("Affordable single room near trotro station",
                        "A simple, clean single room within walking distance of the main trotro stop. It is "
                                + "a good option if you want low rent and an easy commute. The shared bathroom "
                                + "and kitchen are cleaned every morning.",
                        PropertyType.SINGLE_ROOM, "420", FurnishedStatus.UNFURNISHED, 1, 1,
                        "2 Station Road", "Shai Hills", 5.8790, 0.0485, 75,
                        Set.of("Close to transport", "Shared kitchen", "Daily cleaning"), false),
                new DemoListing("Modern one-bedroom apartment",
                        "A modern one-bedroom apartment with a separate living room, fitted kitchen and a "
                                + "private balcony. It is ideal for a final-year student or a couple. Water "
                                + "is stored in an underground tank and the gate is locked at night.",
                        PropertyType.APARTMENT, "900", FurnishedStatus.SEMI_FURNISHED, 1, 1,
                        "11 Hilltop Drive", "Oyibi", 5.9250, 0.0180, 50,
                        Set.of("Balcony", "Fitted kitchen", "Water storage", "Night security"), false),
                new DemoListing("Student hostel room with Wi-Fi and water",
                        "A single hostel room with Wi-Fi, a reading desk and a shared kitchenette. The "
                                + "hostel has a standby water tank and a small shop downstairs. It is a short "
                                + "trotro ride from the Palm University gate.",
                        PropertyType.HOSTEL, "600", FurnishedStatus.FURNISHED, 1, 1,
                        "Hostel Block A, Oyibi Road", "Oyibi", 5.9185, 0.0225, 35,
                        Set.of("Wi-Fi", "Reading desk", "Water tank", "Shared kitchenette"), false),
                new DemoListing("Twin room for two friends sharing",
                        "A spacious twin room that two friends can share comfortably. Each student gets a "
                                + "bed, wardrobe and desk, and the rent shown covers the whole room. There is "
                                + "a private bathroom and a small study table by the window.",
                        PropertyType.SINGLE_ROOM, "560", FurnishedStatus.FURNISHED, 1, 1,
                        "9 Friendship Lane", "Amasaman", 5.8600, 0.0730, 65,
                        Set.of("Twin beds", "Private bathroom", "Study table", "Wardrobes"), false),
                new DemoListing("Self-contained studio with balcony",
                        "A bright self-contained studio with a small balcony facing the morning sun. It has "
                                + "a kitchenette, a private bathroom and enough space for a sofa and a desk. "
                                + "The landlord asks for two months' rent upfront.",
                        PropertyType.STUDIO, "820", FurnishedStatus.FURNISHED, 1, 1,
                        "26 Sunrise Close", "Dodowa", 5.9255, 0.0910, 70,
                        Set.of("Balcony", "Kitchenette", "Private bathroom"), false),
                new DemoListing("Spacious room in a quiet family compound",
                        "A spacious room in a quiet family compound with mature trees and a shared laundry "
                                + "line. The family lives on site and keeps the gate locked in the evening. "
                                + "It is best suited to a student who values calm over nightlife.",
                        PropertyType.SINGLE_ROOM, "500", FurnishedStatus.UNFURNISHED, 1, 1,
                        "44 Family Close", "Amasaman", 5.8525, 0.0675, 80,
                        Set.of("Family compound", "Quiet area", "Shared laundry", "Night security"), false),
                new DemoListing("Furnished apartment with two ensuite rooms",
                        "A furnished two-bedroom apartment with an ensuite bathroom in each room. The living "
                                + "area opens onto a small terrace, and the kitchen is fully fitted. It works "
                                + "well for two students who want privacy and a shared living space.",
                        PropertyType.APARTMENT, "1200", FurnishedStatus.FURNISHED, 2, 2,
                        "15 Palm Ridge", "Shai Hills", 5.9090, 0.0400, 45,
                        Set.of("Two ensuite rooms", "Terrace", "Fitted kitchen", "Gated"), false),
                new DemoListing("Budget hostel bed with utilities included",
                        "A simple hostel bed in a shared four-bed room with electricity, water and Wi-Fi "
                                + "included in the monthly price. There is a shared study room and a common "
                                + "kitchen. It is the lowest-cost option near campus.",
                        PropertyType.HOSTEL, "400", FurnishedStatus.FURNISHED, 1, 1,
                        "Hostel Block B, Palm Junction", "Shai Hills", 5.8860, 0.0310, 30,
                        Set.of("Utilities included", "Wi-Fi", "Study room", "Common kitchen"), false),
                new DemoListing("Newly built chamber and hall",
                        "Brand new chamber and hall self-contained unit, still awaiting its first tenant. "
                                + "Fresh paint, new tiles and a private entrance. Pictures show the finished "
                                + "rooms and the shared compound. This listing is waiting for admin review.",
                        PropertyType.APARTMENT, "950", FurnishedStatus.SEMI_FURNISHED, 2, 2,
                        "17 New Site Avenue", "Shai Hills", 5.8980, 0.0350, 30,
                        Set.of("New build", "Private entrance", "Tiled floors"), true)
        );
    }

    private User user(String email, String fullName, UserRole role, UserStatus status,
                      String phone, University university, String businessName, String passwordHash) {
        return userRepository.findByEmailIgnoreCase(email).map(existing -> {
            boolean changed = false;
            // Keep the published demo logins usable even if an earlier seed
            // left the account without a password or with a stale one.
            if (existing.getPasswordHash() == null
                    || !passwordEncoder.matches(DEMO_PASSWORD, existing.getPasswordHash())) {
                existing.setPasswordHash(passwordHash);
                changed = true;
            }
            if (!existing.isEmailVerified()) {
                existing.setEmailVerified(true);
                changed = true;
            }
            return changed ? userRepository.save(existing) : existing;
        }).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setRole(role);
            user.setStatus(status);
            user.setPhone(phone);
            user.setUniversity(university);
            user.setBusinessName(businessName);
            user.setPasswordHash(passwordHash);
            user.setEmailVerified(true);
            return userRepository.save(user);
        });
    }

    private Property property(University university, User landlord, DemoListing listing, int index,
                              Instant createdAt) {
        Property property = new Property();
        property.setUniversity(university);
        property.setLandlord(landlord);
        property.setTitle(listing.title());
        property.setDescription(listing.description());
        property.setPropertyType(listing.type());
        property.setRentAmount(new BigDecimal(listing.rent()));
        property.setNegotiable(false);
        property.setUtilitiesIncluded(index % 3 == 0);
        property.setUtilitiesDescription(index % 3 == 0 ? "Water and electricity included" : null);
        property.setFurnished(listing.furnished());
        property.setBedrooms(listing.bedrooms());
        property.setBathrooms(listing.bathrooms());
        property.setAddress(listing.address());
        property.setCity(listing.city());
        property.setRegion("Greater Accra");
        property.setLatitude(listing.latitude());
        property.setLongitude(listing.longitude());
        property.setDistanceToCampusKm(GeoUtils.roundKm(GeoUtils.haversineKm(
                listing.latitude(), listing.longitude(),
                university.getLatitude(), university.getLongitude())));
        property.setMoveInDate(LocalDate.now().plusDays(listing.moveInDays()));
        property.setLeaseTerms("12 months preferred; shorter stays considered for students.");
        property.setStatus(listing.pending() ? ListingStatus.PENDING_APPROVAL : ListingStatus.APPROVED);
        property.setAmenities(new LinkedHashSet<>(listing.amenities()));
        property.setCreatedAt(createdAt);
        propertyRepository.save(property);

        for (int imageIndex = 0; imageIndex < 2; imageIndex++) {
            String fileName = "demo-" + property.getId() + "-" + imageIndex + ".png";
            byte[] png = demoImage(property.getTitle(), imageIndex);
            fileStorageService.storeSeedImage(png, fileName);
            PropertyImage image = new PropertyImage();
            image.setProperty(property);
            image.setFileName(fileName);
            image.setOriginalName(DEMO_IMAGE_PREFIX + property.getId() + "-" + (imageIndex + 1) + ".png");
            image.setContentType("image/png");
            image.setFileSize(png.length);
            image.setCover(imageIndex == 0);
            image.setSortOrder(imageIndex);
            imageRepository.save(image);
            property.getImages().add(image);
        }
        return property;
    }

    /**
     * The free Render plan resets the filesystem on every restart. Rebuild any
     * sample photo that is referenced by the database but no longer on disk.
     */
    private void repairMissingDemoImages() {
        int repaired = 0;
        for (PropertyImage image : imageRepository.findAll()) {
            if (image.getOriginalName() == null || !image.getOriginalName().startsWith(DEMO_IMAGE_PREFIX)) {
                continue;
            }
            if (fileStorageService.seedImageExists(image.getFileName())) {
                continue;
            }
            Property property = image.getProperty();
            int variant = Math.max(0, image.getSortOrder());
            byte[] png = demoImage(property.getTitle(), variant);
            fileStorageService.storeSeedImage(png, image.getFileName());
            image.setFileSize(png.length);
            imageRepository.save(image);
            repaired++;
        }
        if (repaired > 0) {
            log.info("Regenerated {} missing demo listing photos", repaired);
        }
    }

    private byte[] demoImage(String title, int variant) {
        Color[] palette = PALETTES[Math.floorMod(title.hashCode(), PALETTES.length)];
        Color color = palette[Math.min(variant, palette.length - 1)];
        return placeholderPng(title, color, variant == 0 ? "Living space" : "Room detail",
                900 + variant * 40);
    }

    private StudentPost post(User author, University university, String title, String body,
                             String preferredLocation, String budget, int roommatesNeeded) {
        StudentPost post = new StudentPost();
        post.setAuthor(author);
        post.setUniversity(university);
        post.setTitle(title);
        post.setBody(body);
        post.setPreferredLocation(preferredLocation);
        post.setBudgetMax(new BigDecimal(budget));
        post.setRoommatesNeeded(roommatesNeeded);
        post.setStatus(ModerationStatus.VISIBLE);
        return postRepository.save(post);
    }

    private void comment(StudentPost post, User author, String body) {
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setBody(body);
        comment.setStatus(ModerationStatus.VISIBLE);
        commentRepository.save(comment);
    }

    private SavedProperty save(User student, Property property) {
        SavedProperty saved = new SavedProperty();
        saved.setUser(student);
        saved.setProperty(property);
        return savedPropertyRepository.save(saved);
    }

    private byte[] placeholderPng(String title, Color from, String caption, int width) {
        int height = 640;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setPaint(new GradientPaint(0, 0, from, width, height, from.brighter()));
            graphics.fillRect(0, 0, width, height);

            graphics.setColor(new Color(255, 255, 255, 38));
            graphics.fillRoundRect(70, 90, width - 140, height - 230, 36, 36);
            graphics.setColor(new Color(255, 255, 255, 22));
            graphics.fillRoundRect(120, 150, width - 240, 210, 28, 28);

            graphics.setColor(new Color(255, 255, 255, 235));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            graphics.drawString("UniLodge", 80, 60);

            graphics.setColor(new Color(255, 255, 255, 200));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
            graphics.drawString("Student accommodation · Ghana", 80, height - 80);

            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            drawWrapped(graphics, title, 130, 260, width - 260, 40);
            graphics.setColor(new Color(255, 255, 255, 190));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
            graphics.drawString(caption, 130, 430);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate placeholder image", exception);
        }
    }

    private void drawWrapped(Graphics2D graphics, String text, int x, int y, int maxWidth, int lineHeight) {
        StringBuilder line = new StringBuilder();
        int cursorY = y;
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (graphics.getFontMetrics().stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                graphics.drawString(line.toString(), x, cursorY);
                cursorY += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            graphics.drawString(line.toString(), x, cursorY);
        }
    }
}
