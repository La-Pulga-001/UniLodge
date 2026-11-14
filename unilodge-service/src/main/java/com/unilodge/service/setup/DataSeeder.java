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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * First-run bootstrap: seeds the three original administrators and, when
 * enabled for local development, a realistic set of demo accounts, listings,
 * posts and comments.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

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
        if (propertyRepository.count() > 0) {
            return;
        }
        University palm = universityRepository.findByNameIgnoreCase("Palm University College")
                .orElseGet(() -> universityRepository.findByActiveTrueOrderByNameAsc().stream()
                        .findFirst()
                        .orElse(null));
        if (palm == null) {
            log.warn("Demo data needs at least one active university; skipping.");
            return;
        }

        String demoPassword = passwordEncoder.encode("Demo@1234");

        User student = user("kwame.student@unilodge.test", "Kwame Boateng", UserRole.STUDENT,
                UserStatus.ACTIVE, "+233 24 555 0102", palm, null, demoPassword);
        User landlord = user("ama.landlord@unilodge.test", "Ama Mensah", UserRole.LANDLORD,
                UserStatus.ACTIVE, "+233 24 555 0101", null, "Ama Student Homes", demoPassword);
        User pendingLandlord = user("kofi.landlord@unilodge.test", "Kofi Owusu", UserRole.LANDLORD,
                UserStatus.PENDING_APPROVAL, "+233 20 555 0103", null, "Owusu Properties", demoPassword);

        List<Property> approved = new ArrayList<>();
        approved.add(property(palm, landlord, "Sunny single room near Palm University",
                "A bright, furnished single room a short walk from campus. The compound is fenced, has "
                        + "a shared kitchen and reliable water storage. Perfect for a first-year student "
                        + "who wants a calm, secure place to study.",
                PropertyType.SINGLE_ROOM, "450", FurnishedStatus.FURNISHED, 1, 1,
                "12 Palm Estate Road", "Shai Hills", 5.8955, 0.0410, 45,
                Set.of("Wi-Fi", "Water storage", "Fenced compound", "Shared kitchen"), 0));
        approved.add(property(palm, landlord, "Modern self-contained studio in Shai Hills",
                "Newly finished self-contained studio with its own kitchenette and bathroom. Tiled "
                        + "throughout, prepaid meter, and space for a study desk. Great natural light "
                        + "during the day and a quiet neighbourhood at night.",
                PropertyType.STUDIO, "780", FurnishedStatus.SEMI_FURNISHED, 1, 1,
                "5 Baatsona Close", "Shai Hills", 5.8890, 0.0330, 45,
                Set.of("Prepaid meter", "Kitchenette", "Private bathroom", "Parking"), 1));
        approved.add(property(palm, landlord, "Two-bedroom apartment for sharing",
                "Spacious two-bedroom apartment that works beautifully for two students sharing. "
                        + "Large living area, fitted kitchen, and a balcony overlooking a quiet street. "
                        + "Rent can be split between roommates.",
                PropertyType.APARTMENT, "1100", FurnishedStatus.FURNISHED, 2, 1,
                "22 Green Valley Lane", "Shai Hills", 5.9010, 0.0270, 60,
                Set.of("Balcony", "Fitted kitchen", "Wi-Fi ready", "Gated"), 2));
        approved.add(property(palm, landlord, "Budget room in a friendly shared house",
                "An affordable room in a friendly shared house with two other students. Utilities are "
                        + "shared fairly and the landlord lives nearby, so repairs are handled quickly. "
                        + "Bring your own mattress if you prefer.",
                PropertyType.SINGLE_ROOM, "520", FurnishedStatus.UNFURNISHED, 1, 1,
                "8 Market Road", "Shai Hills", 5.8840, 0.0450, 90,
                Set.of("Shared bathroom", "Water tank", "Close to trotro stop"), 3));
        approved.add(property(palm, landlord, "Quiet hostel room with utilities included",
                "A calm hostel room with electricity and water included in the rent. There is a "
                        + "shared study room on the ground floor and a caretaker on site every day. "
                        + "Ideal if you want one predictable monthly cost.",
                PropertyType.HOSTEL, "650", FurnishedStatus.FURNISHED, 1, 1,
                "Hostel Block C, Palm Junction", "Shai Hills", 5.8875, 0.0295, 30,
                Set.of("Utilities included", "Study room", "Caretaker", "Wi-Fi"), 4));

        property(palm, pendingLandlord, "Newly built chamber and hall",
                "Brand new chamber and hall self-contained unit, still awaiting its first tenant. "
                        + "Fresh paint, new tiles and a private entrance. Pictures show the finished "
                        + "rooms and the shared compound.",
                PropertyType.APARTMENT, "950", FurnishedStatus.SEMI_FURNISHED, 2, 2,
                "17 New Site Avenue", "Shai Hills", 5.8980, 0.0350, 30,
                Set.of("New build", "Private entrance", "Tiled floors"), 5);

        // Roommate requests and a discussion thread.
        StudentPost postOne = post(student, palm, "Looking for a roommate to share a two-bedroom",
                "I am a second-year student at Palm University looking for one calm, tidy roommate to "
                        + "share a two-bedroom apartment close to campus. I cook often, keep shared spaces "
                        + "clean and I am happy to split rent and utilities equally.",
                "Shai Hills / near campus", "600", 1);
        StudentPost postTwo = post(student, palm, "Female student looking for a furnished room",
                "Hello! I am starting at Palm University this semester and looking for a furnished room "
                        + "with reliable water and Wi-Fi. My budget is around GH₵600 a month and I would "
                        + "like somewhere within walking distance of campus.",
                "Within walking distance of Palm University", "600", 1);

        comment(postOne, student, "I am also looking around Shai Hills. Sending you a message!");
        comment(postOne, landlord, "I have a two-bedroom apartment that is perfect for sharing, "
                + "see my listing 'Two-bedroom apartment for sharing'.");
        comment(postTwo, student, "Update: I have viewed two places and would love more options "
                + "with utilities included.");

        SavedProperty first = save(student, approved.get(0));
        SaveMemento.remember(first);
        save(student, approved.get(2));

        log.info("""

                Demo data ready (development only)
                  Student  : kwame.student@unilodge.test / Demo@1234
                  Landlord : ama.landlord@unilodge.test / Demo@1234 (approved)
                  Landlord : kofi.landlord@unilodge.test / Demo@1234 (awaiting approval)
                """);
    }

    /** Small holder so the compiler does not complain about unused results. */
    private static final class SaveMemento {
        private static SavedProperty last;

        static void remember(SavedProperty saved) {
            last = saved;
        }
    }

    private User user(String email, String fullName, UserRole role, UserStatus status,
                      String phone, University university, String businessName, String passwordHash) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
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

    private Property property(University university, User landlord, String title, String description,
                              PropertyType type, String rent, FurnishedStatus furnished, int bedrooms,
                              int bathrooms, String address, String city, double latitude, double longitude,
                              int daysUntilMoveIn, Set<String> amenities, int imageSeed) {
        Property property = new Property();
        property.setUniversity(university);
        property.setLandlord(landlord);
        property.setTitle(title);
        property.setDescription(description);
        property.setPropertyType(type);
        property.setRentAmount(new BigDecimal(rent));
        property.setNegotiable(false);
        property.setUtilitiesIncluded(imageSeed % 2 == 0);
        property.setUtilitiesDescription(imageSeed % 2 == 0 ? "Water and electricity included" : null);
        property.setFurnished(furnished);
        property.setBedrooms(bedrooms);
        property.setBathrooms(bathrooms);
        property.setAddress(address);
        property.setCity(city);
        property.setRegion("Greater Accra");
        property.setLatitude(latitude);
        property.setLongitude(longitude);
        property.setDistanceToCampusKm(GeoUtils.roundKm(GeoUtils.haversineKm(
                latitude, longitude, university.getLatitude(), university.getLongitude())));
        property.setMoveInDate(LocalDate.now().plusDays(daysUntilMoveIn));
        property.setLeaseTerms("12 months preferred; shorter stays considered for students.");
        property.setStatus(imageSeed == 5 ? ListingStatus.PENDING_APPROVAL : ListingStatus.APPROVED);
        property.setAmenities(new LinkedHashSet<>(amenities));
        propertyRepository.save(property);

        Color[][] palettes = {
                {new Color(0x2E6B52), new Color(0xA8C9A0)},
                {new Color(0x1F4E5F), new Color(0x9FC6C9)},
                {new Color(0x6B4F2E), new Color(0xD5C0A0)},
                {new Color(0x3F5A3A), new Color(0xC2D6A4)},
                {new Color(0x4A3F63), new Color(0xB9AED6)}
        };
        Color[] palette = palettes[imageSeed % palettes.length];
        for (int index = 0; index < 2; index++) {
            byte[] png = placeholderPng(title, palette[index % palette.length],
                    index == 0 ? "Living space" : "Room detail", 900 + index * 40);
            PropertyImage image = new PropertyImage();
            image.setProperty(property);
            image.setFileName(fileStorageService.storeSeedImage(png));
            image.setOriginalName("unilodge-demo-" + (imageSeed + 1) + "-" + (index + 1) + ".png");
            image.setContentType("image/png");
            image.setFileSize(png.length);
            image.setCover(index == 0);
            image.setSortOrder(index);
            imageRepository.save(image);
            property.getImages().add(image);
        }
        return property;
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
