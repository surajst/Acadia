package com.schoolos;

import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.management.AcademicSubmission;
import com.schoolos.management.AcademicSubmissionRepository;
import com.schoolos.management.SchoolClass;
import com.schoolos.management.SchoolClassRepository;
import com.schoolos.management.RewardItem;
import com.schoolos.management.RewardItemRepository;
import com.schoolos.management.Parent;
import com.schoolos.management.ParentRepository;
import com.schoolos.management.ParentReward;
import com.schoolos.management.ParentRewardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@Order(1)
@ConditionalOnProperty(name = "app.dev-mode", havingValue = "true")
public class AcademicDataSeeder implements CommandLineRunner {

    @Autowired
    private ClassSectionRepository classSectionRepo;

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private AcademicSubmissionRepository submissionRepo;

    @Autowired
    private SchoolClassRepository schoolClassRepo;

    @Autowired
    private RewardItemRepository rewardItemRepo;

    @Autowired
    private ParentRepository parentRepo;

    @Autowired
    private ParentRewardRepository parentRewardRepo;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${demo.admin.password:PilotLaunchSecure2026!}")
    private String demoAdminPassword;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID teacher1Id = UUID.nameUUIDFromBytes("teacher_1".getBytes());
        UUID classId = UUID.fromString("66666666-6666-6666-6666-666666666666"); // Class 6 A
        UUID academicYearId = UUID.fromString("00000000-0000-0000-0000-111111111111");

        // Ensure static Tenant exists to satisfy foreign keys in H2
        Integer tenantCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tenants WHERE id = ?", 
            Integer.class, 
            tenantId
        );
        if (tenantCount == null || tenantCount == 0) {
            jdbcTemplate.update(
                "INSERT INTO tenants (id, name, subdomain, is_active, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                tenantId, "Greenwood High Static", "greenwood_static", true
            );
        }

        // Ensure static Academic Year exists
        Integer academicYearCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM academic_years WHERE id = ?", 
            Integer.class, 
            academicYearId
        );
        if (academicYearCount == null || academicYearCount == 0) {
            jdbcTemplate.update(
                "INSERT INTO academic_years (id, tenant_id, name, start_date, end_date, is_current) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE, ?)",
                academicYearId, tenantId, "2026-2027", true
            );
        }

        // Ensure static User exists to prevent EmptyResultDataAccessException in DemoTestHarness
        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users", 
            Integer.class
        );
        if (userCount == null || userCount == 0) {
            UUID activeUserId = UUID.fromString("22222222-3333-4444-5555-666666666666");
            jdbcTemplate.update(
                "INSERT INTO users (id, tenant_id, academic_year_id, email, password_hash, full_name, role, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", 
                activeUserId, tenantId, academicYearId, "admin@greenwood.com", passwordEncoder.encode(demoAdminPassword), "Admin User", "ADMIN", true
            );
        } else {
            jdbcTemplate.update(
                "UPDATE users SET password_hash = ? WHERE email = ?",
                passwordEncoder.encode(demoAdminPassword), "admin@greenwood.com"
            );
        }

        // 1. Seed or Update the Class Sections for teachers
        String[] teachers = {"teacher_1", "teacher_2", "teacher_3", "teacher_4", "teacher_5"};
        int[] grades = {6, 7, 8, 9, 10};
        UUID[] classIds = {
            classId,
            UUID.fromString("77777777-7777-7777-7777-777777777777"),
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            UUID.fromString("10101010-1010-1010-1010-101010101010")
        };

        for (int i = 0; i < 5; i++) {
            UUID secTeacherId = UUID.nameUUIDFromBytes(teachers[i].getBytes());
            UUID secClassId = classIds[i];

            if (!classSectionRepo.existsById(secClassId)) {
                ClassSection section = new ClassSection();
                section.setId(secClassId);
                section.setTenantId(tenantId);
                section.setAcademicYearId(academicYearId);
                section.setGradeName("Grade " + grades[i]);      
                section.setSectionName("A");            
                section.setRoomNumber("Room 20" + (4 + i));
                section.setTeacherId(secTeacherId);
                classSectionRepo.save(section);
            } else {
                ClassSection existingSection = classSectionRepo.findById(secClassId).get();
                existingSection.setGradeName("Grade " + grades[i]);
                existingSection.setSectionName("A");
                existingSection.setTeacherId(secTeacherId);
                classSectionRepo.save(existingSection);
            }
        }

        // 2. Seed the Students if they aren't linked yet
        UUID arjunId = UUID.fromString("00000000-0000-0000-0000-000000000091");
        if (!studentRepo.existsById(arjunId)) {
            ClassSection savedSection = classSectionRepo.findById(classId).orElse(null);

            if (savedSection != null) {
                UUID ananyaId = UUID.fromString("00000000-0000-0000-0000-000000000092");

                Student student1 = new Student();
                student1.setId(arjunId);
                student1.setTenantId(tenantId);
                student1.setAcademicYearId(academicYearId);
                student1.setFirstName("Arjun");
                student1.setLastName("Sharma");
                student1.setRollNumber("6A-01");
                student1.setClassSection(savedSection);
                studentRepo.save(student1);

                Student student2 = new Student();
                student2.setId(ananyaId);
                student2.setTenantId(tenantId);
                student2.setAcademicYearId(academicYearId);
                student2.setFirstName("Ananya");
                student2.setLastName("Iyer");
                student2.setRollNumber("6A-02");
                student2.setClassSection(savedSection);
                studentRepo.save(student2);

                System.out.println("Automated Seeder -> Synced students perfectly under Grade 6 - A");
            }
        }

        // 3. Seed pending AcademicSubmission for "Arjun Sharma" if empty
        if (submissionRepo.count() == 0) {
            Student arjun = studentRepo.findById(arjunId).orElse(null);
            
            if (arjun != null) {
                AcademicSubmission submission = new AcademicSubmission();
                submission.setId(UUID.randomUUID());
                submission.setStudentId(arjunId);
                submission.setSkillName("6th Grade Fraction Mastery");
                submission.setXpBounty(250);
                submission.setStatus("PENDING");
                submission.setSubmittedAt(LocalDateTime.now());
                submissionRepo.save(submission);
                System.out.println("Automated Seeder -> Injected a pending Arjun Sharma milestone submission!");
            }
        }

        // 4. Seed SchoolClasses and link Students if empty
        if (schoolClassRepo.count() == 0) {
            SchoolClass class1 = new SchoolClass();
            UUID class1Id = UUID.fromString("11111111-2222-3333-4444-555555555551");
            class1.setId(class1Id);
            class1.setTenantId(tenantId);
            class1.setAcademicYearId(academicYearId);
            class1.setGradeLevel("Grade 6");
            class1.setSectionName("A");
            class1.setRoomNumber("Room 204");
            class1.setTotalCapacity(30);
            schoolClassRepo.save(class1);

            SchoolClass class2 = new SchoolClass();
            UUID class2Id = UUID.fromString("11111111-2222-3333-4444-555555555552");
            class2.setId(class2Id);
            class2.setTenantId(tenantId);
            class2.setAcademicYearId(academicYearId);
            class2.setGradeLevel("Grade 6");
            class2.setSectionName("B");
            class2.setRoomNumber("Room 205");
            class2.setTotalCapacity(30);
            schoolClassRepo.save(class2);

            SchoolClass class3 = new SchoolClass();
            UUID class3Id = UUID.fromString("11111111-2222-3333-4444-555555555553");
            class3.setId(class3Id);
            class3.setTenantId(tenantId);
            class3.setAcademicYearId(academicYearId);
            class3.setGradeLevel("Grade 5");
            class3.setSectionName("A");
            class3.setRoomNumber("Room 102");
            class3.setTotalCapacity(25);
            schoolClassRepo.save(class3);

            System.out.println("Automated Seeder -> Synced premium SchoolClass entries!");

            // Link existing students in the database to Class 1 (Grade 6 - A)
            List<Student> seededStudents = studentRepo.findByTenantId(tenantId);
            for (Student student : seededStudents) {
                if ("Arjun".equals(student.getFirstName()) || "Ananya".equals(student.getFirstName())) {
                    student.setSchoolClass(class1);
                    studentRepo.save(student);
                }
            }
            System.out.println("Automated Seeder -> Linked Arjun and Ananya to Grade 6 - A SchoolClass!");
        }

        // 5. Seed RewardItems if empty
        if (rewardItemRepo.count() == 0) {
            RewardItem reward1 = new RewardItem(
                UUID.fromString("22222222-2222-2222-2222-222222222221"),
                "Honor Roll Certificate",
                "Receive a formal certificate of merit from the school administration recognizing your advanced milestone achievements.",
                250,
                "📜",
                50
            );
            reward1.setTenantId(tenantId);
            reward1.setAcademicYearId(academicYearId);
            rewardItemRepo.save(reward1);

            RewardItem reward2 = new RewardItem(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Library Premium Pass",
                "Gain exclusive early borrowing access to new academic book releases and priority booking for premium quiet study booths.",
                500,
                "📚",
                20
            );
            reward2.setTenantId(tenantId);
            reward2.setAcademicYearId(academicYearId);
            rewardItemRepo.save(reward2);

            RewardItem reward3 = new RewardItem(
                UUID.fromString("22222222-2222-2222-2222-222222222223"),
                "Academic Mentorship Session",
                "Schedule a private, one-on-one academic counseling and advanced math mentorship session with the school's lead educator.",
                750,
                "👨‍🏫",
                15
            );
            reward3.setTenantId(tenantId);
            reward3.setAcademicYearId(academicYearId);
            rewardItemRepo.save(reward3);

            RewardItem reward4 = new RewardItem(
                UUID.fromString("22222222-2222-2222-2222-222222222224"),
                "Science Explorer Kit",
                "Receive a complete, take-home science lab kit to perform safe, hands-on physics and chemistry experiment milestones.",
                1000,
                "🧪",
                10
            );
            reward4.setTenantId(tenantId);
            reward4.setAcademicYearId(academicYearId);
            rewardItemRepo.save(reward4);

            System.out.println("Automated Seeder -> Synced default RewardItem entries!");
        }

        // 6. Seed Parent and Pending ParentReward for Arjun Sharma
        Student arjun = studentRepo.findById(arjunId).orElse(null);
        if (arjun != null) {
            long parentCountForArjun = arjun.getParents().size();
            if (parentCountForArjun == 0) {
                Parent parent = new Parent();
                parent.setId(UUID.fromString("99999999-9999-9999-9999-999999999991"));
                parent.setTenantId(tenantId);
                parent.setAcademicYearId(academicYearId);
                parent.setFirstName("Ramesh");
                parent.setLastName("Sharma");
                parent.setPhoneNumber("+91 99887 76655");
                parent.setEmail("ramesh.sharma@example.com");
                parentRepo.save(parent);

                arjun.getParents().add(parent);
                studentRepo.save(arjun);

                System.out.println("Automated Seeder -> Synced Ramesh Sharma as parent for Arjun Sharma!");
            }
            
            if (parentRewardRepo.findByStudentIdAndStatus(arjunId, "PENDING").isEmpty()) {
                Parent parent = arjun.getParents().isEmpty() ? null : arjun.getParents().iterator().next();
                if (parent == null) {
                    parent = parentRepo.findById(UUID.fromString("99999999-9999-9999-9999-999999999991")).orElse(null);
                }
                if (parent != null) {
                    ParentReward pendingReward = new ParentReward();
                    pendingReward.setId(UUID.randomUUID());
                    pendingReward.setTenantId(tenantId);
                    pendingReward.setAcademicYearId(academicYearId);
                    pendingReward.setParent(parent);
                    pendingReward.setStudent(arjun);
                    pendingReward.setRewardTitle("Library Premium Pass");
                    pendingReward.setXpCost(500);
                    pendingReward.setStatus("PENDING");
                    parentRewardRepo.save(pendingReward);
                    System.out.println("Automated Seeder -> Synced pending parent reward co-sign for Arjun Sharma!");
                }
            }
        }
        
        // 7. Seed Pilot Parents and Students for Dry-Run
        for (int i = 1; i <= 3; i++) {
            UUID pilotStudentId = UUID.fromString(String.format("88888888-8888-8888-8888-88888888888%d", i));
            if (!studentRepo.existsById(pilotStudentId)) {
                ClassSection savedSection = classSectionRepo.findById(classIds[1]).orElse(null); // Assign to class 7 A
                if (savedSection != null) {
                    Student pilotStudent = new Student();
                    pilotStudent.setId(pilotStudentId);
                    pilotStudent.setTenantId(tenantId);
                    pilotStudent.setAcademicYearId(academicYearId);
                    pilotStudent.setFirstName("PilotStudent");
                    pilotStudent.setLastName(String.valueOf(i));
                    pilotStudent.setRollNumber("Pilot-" + i);
                    pilotStudent.setClassSection(savedSection);
                    
                    Parent pilotParent = new Parent();
                    pilotParent.setId(UUID.fromString(String.format("77777777-7777-7777-7777-77777777777%d", i)));
                    pilotParent.setTenantId(tenantId);
                    pilotParent.setAcademicYearId(academicYearId);
                    pilotParent.setFirstName("PilotParent");
                    pilotParent.setLastName(String.valueOf(i));
                    pilotParent.setPhoneNumber("+91 00000 0000" + i);
                    pilotParent.setEmail("parent_" + i + "@pilotschool.edu");
                    parentRepo.save(pilotParent);
                    
                    pilotStudent.getParents().add(pilotParent);
                    studentRepo.save(pilotStudent);
                    System.out.println("Automated Seeder -> Synced Pilot Student and Parent " + i + " into Grade 7");
                }
            }
        }
        // 7. Seed Curriculum Data
        seedCurriculumData(tenantId, academicYearId);
    } // Closes the run method

    private void seedCurriculumData(UUID tenantId, UUID academicYearId) {
        String sql = "INSERT INTO curriculums (id, tenant_id, academic_year_id, syllabus_type, standard, subject_type, topic_name, topic_order, xp_reward) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                     
        // SCIENCE
        seedClassSubject(tenantId, academicYearId, sql, 5, "SCIENCE", 40, new String[]{
            "Food and Health", "Bones and Muscles", "Rocks and Soil", "Matter and Materials", "Our Environment", "Animals and Their Habitats", "Plants and Their Habitats", "Air Water Weather", "Light and Sound", "Simple Machines"
        });
        seedClassSubject(tenantId, academicYearId, sql, 6, "SCIENCE", 50, new String[]{
            "The Wonderful World of Science", "Diversity in the Living World", "Mindful Eating A Path to a Healthy Body", "Exploring Magnets", "Separation of Substances", "Changes Around Us", "Getting to Know Plants", "Body Movements", "The Living Organisms and Their Surroundings", "Motion and Measurement of Distances", "Light Shadows and Reflections", "Electricity and Circuits"
        });
        seedClassSubject(tenantId, academicYearId, sql, 7, "SCIENCE", 60, new String[]{
            "Nutrition in Plants", "Nutrition in Animals", "Heat", "Acids Bases and Salts", "Physical and Chemical Changes", "Respiration in Organisms", "Transportation in Animals and Plants", "Reproduction in Plants", "Motion and Time", "Electric Current and Its Effects", "Light", "Forests Our Lifeline", "Wastewater Story"
        });
        seedClassSubject(tenantId, academicYearId, sql, 8, "SCIENCE", 70, new String[]{
            "Crop Production and Management", "Microorganisms Friend and Foe", "Coal and Petroleum", "Combustion and Flame", "Conservation of Plants and Animals", "Reproduction in Animals", "Reaching the Age of Adolescence", "Force and Pressure", "Friction", "Sound", "Chemical Effects of Electric Current", "Some Natural Phenomena", "Light", "Stars and the Solar System", "Pollution of Air and Water", "Materials Metals and Non Metals", "Synthetic Fibres and Plastics", "Cell Structure and Functions"
        });
        seedClassSubject(tenantId, academicYearId, sql, 9, "SCIENCE", 80, new String[]{
            "Matter in Our Surroundings", "Is Matter Around Us Pure", "Atoms and Molecules", "Structure of the Atom", "The Fundamental Unit of Life", "Tissues", "Motion", "Force and Laws of Motion", "Gravitation", "Work and Energy", "Sound", "Improvement in Food Resources", "Why Do We Fall Ill", "Natural Resources", "Diversity in Living Organisms"
        });
        seedClassSubject(tenantId, academicYearId, sql, 10, "SCIENCE", 100, new String[]{
            "Chemical Reactions and Equations", "Acids Bases and Salts", "Metals and Non Metals", "Carbon and Its Compounds", "Periodic Classification of Elements", "Life Processes", "Control and Coordination", "How Do Organisms Reproduce", "Heredity and Evolution", "Light Reflection and Refraction", "Human Eye and Colourful World", "Electricity", "Magnetic Effects of Electric Current", "Sources of Energy", "Our Environment", "Management of Natural Resources"
        });
        
        // SOCIAL SCIENCE
        seedClassSubject(tenantId, academicYearId, sql, 5, "SOCIAL_SCIENCE", 40, new String[]{
            "Our Environment", "Maps and Globes", "Major Landforms", "Our Country India", "The Mughal Empire", "The Colonial Period", "Local Government", "Our Constitution"
        });
        seedClassSubject(tenantId, academicYearId, sql, 6, "SOCIAL_SCIENCE", 50, new String[]{
            "What Where How and When", "On the Trail of Earliest People", "From Gathering to Growing Food", "In the Earliest Cities", "Kingdoms Kings and Early Republic", "New Questions and Ideas", "The Earth Our Habitat", "Motions of the Earth", "Maps", "Social and Political Life"
        });
        seedClassSubject(tenantId, academicYearId, sql, 7, "SOCIAL_SCIENCE", 60, new String[]{
            "Tracing Changes Through a Thousand Years", "New Kings and Kingdoms", "The Delhi Sultans", "The Mughal Empire", "Rulers and Buildings", "Environment", "Inside Our Earth", "Our Changing Earth", "Air", "Democracy and Equality"
        });
        seedClassSubject(tenantId, academicYearId, sql, 8, "SOCIAL_SCIENCE", 70, new String[]{
            "How When and Where", "From Trade to Territory", "Ruling the Countryside", "Tribals Dikus and the Vision of a Golden Age", "When People Rebel", "Weavers Iron Smelters and Factory Owners", "Resources", "Land Soil Water Natural Vegetation and Wildlife", "Agriculture", "Industries"
        });
        seedClassSubject(tenantId, academicYearId, sql, 9, "SOCIAL_SCIENCE", 80, new String[]{
            "The French Revolution", "Socialism in Europe and the Russian Revolution", "Nazism and the Rise of Hitler", "Forest Society and Colonialism", "Pastoralists in the Modern World", "India Size and Location", "Physical Features of India", "Drainage", "Climate", "Democracy in the Contemporary World"
        });
        seedClassSubject(tenantId, academicYearId, sql, 10, "SOCIAL_SCIENCE", 100, new String[]{
            "The Rise of Nationalism in Europe", "Nationalism in India", "The Making of a Global World", "The Age of Industrialisation", "Print Culture and the Modern World", "Resources and Development", "Forest and Wildlife Resources", "Water Resources", "Agriculture", "Minerals and Energy Resources", "Power Sharing", "Federalism"
        });
        
        // ENGLISH
        seedClassSubject(tenantId, academicYearId, sql, 5, "ENGLISH", 40, new String[]{
            "Ice Cream Man", "Wonderful Waste", "My Shadow", "Crying", "The Lazy Frog", "Class Discussion", "The Scholars Mother Tongue", "The Little Bully", "Around the World", "Who Will Be Ningthou"
        });
        seedClassSubject(tenantId, academicYearId, sql, 6, "ENGLISH", 50, new String[]{
            "Who Did Patricks Homework", "How the Dog Found Himself a Master", "Taros Reward", "An Indian American Woman in Space", "A Different Kind of School", "Who I Am", "Fair Play", "A Game of Chance", "Desert Animals", "The Banyan Tree"
        });
        seedClassSubject(tenantId, academicYearId, sql, 7, "ENGLISH", 60, new String[]{
            "Three Questions", "A Gift of Chappals", "Gopal and the Hilsa Fish", "The Ashes That Made Trees Bloom", "Quality", "Expert Detectives", "The Invention of Vita Wonk", "Fire Friend and Foe", "A Bicycle in Good Repair", "The Story of Cricket"
        });
        seedClassSubject(tenantId, academicYearId, sql, 8, "ENGLISH", 70, new String[]{
            "The Best Christmas Present in the World", "The Tsunami", "Glimpses of the Past", "Bepin Choudhurys Lapse of Memory", "The Summit Within", "This is Jodys Fawn", "A Visit to Cambridge", "A Short Monsoon Diary", "The Great Stone Face Part 1", "The Great Stone Face Part 2"
        });
        seedClassSubject(tenantId, academicYearId, sql, 9, "ENGLISH", 80, new String[]{
            "The Fun They Had", "The Sound of Music", "The Little Girl", "A Truly Beautiful Mind", "The Snake and the Mirror", "My Childhood", "Reach for the Top", "Kathmandu", "If I Were You", "The Lake Isle of Innisfree", "A Legend of the Northland"
        });
        seedClassSubject(tenantId, academicYearId, sql, 10, "ENGLISH", 100, new String[]{
            "A Letter to God", "Nelson Mandela Long Walk to Freedom", "Two Stories About Flying", "From the Diary of Anne Frank", "The Hundred Dresses Part 1", "The Hundred Dresses Part 2", "Glimpses of India", "Mijbil the Otter", "Madam Rides the Bus", "The Sermon at Benares"
        });
    }

    private void seedClassSubject(UUID tenantId, UUID academicYearId, String sql, int standard, String subject, int xpReward, String[] topics) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM curriculums WHERE tenant_id = ? AND academic_year_id = ? AND syllabus_type = ? AND standard = ? AND subject_type = ?",
            Integer.class, tenantId, academicYearId, "CBSE", standard, subject
        );
        if (count == null || count == 0) {
            for (int i = 0; i < topics.length; i++) {
                jdbcTemplate.update(sql, UUID.randomUUID(), tenantId, academicYearId, "CBSE", standard, subject, topics[i], i + 1, xpReward);
            }
            System.out.println("Automated Seeder -> Synced CBSE " + subject + " for Class " + standard);
        }
    }
} // Closes the class
