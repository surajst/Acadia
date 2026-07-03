package com.schoolos;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.management.SchoolClass;
import com.schoolos.management.SchoolClassRepository;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
@Order(2)
@ConditionalOnProperty(name = "app.dev-mode", havingValue = "true")
public class ScaleTestDataSeeder implements CommandLineRunner {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private ClassSectionRepository classSectionRepo;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Value("${demo.admin.password:PilotLaunchSecure2026!}")
    private String demoAdminPassword;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        long studentCount = studentRepository.count();
        boolean shouldSeed = System.getProperty("seed.scale") != null 
                          || System.getenv("SEED_SCALE") != null 
                          || studentCount < 100;

        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID academicYearId = UUID.fromString("00000000-0000-0000-0000-111111111111");

        // ALWAYS seed pilot credentials, regardless of scale seed status
        seedPilotDatabaseCredentials(tenantId, academicYearId);

        if (!shouldSeed) {
            System.out.println(">> Scale Simulation Seeder -> Conditions not met. Skipping scale seeding.");
            return;
        }

        System.out.println(">> Scale Simulation Seeder -> Initializing scale data creation (500 students, 20 staff)...");

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

        // 1. Seed SchoolClass and ClassSection layers from KG to 10th Standard (Sections A and B)
        String[] grades = {"KG", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6", "Grade 7", "Grade 8", "Grade 9", "Grade 10"};
        String[] sections = {"A", "B"};
        int roomCounter = 1;

        for (String grade : grades) {
            for (String section : sections) {
                UUID classId;
                UUID sectionId;

                // Reuse pre-existing H2 IDs where applicable to maintain controller and seeder compatibility
                if ("Grade 6".equals(grade) && "A".equals(section)) {
                    classId = UUID.fromString("11111111-2222-3333-4444-555555555551");
                    sectionId = UUID.fromString("66666666-6666-6666-6666-666666666666");
                } else if ("Grade 6".equals(grade) && "B".equals(section)) {
                    classId = UUID.fromString("11111111-2222-3333-4444-555555555552");
                    sectionId = UUID.nameUUIDFromBytes(("classsection-" + grade + "-" + section).getBytes());
                } else if ("Grade 5".equals(grade) && "A".equals(section)) {
                    classId = UUID.fromString("11111111-2222-3333-4444-555555555553");
                    sectionId = UUID.nameUUIDFromBytes(("classsection-" + grade + "-" + section).getBytes());
                } else {
                    classId = UUID.nameUUIDFromBytes(("schoolclass-" + grade + "-" + section).getBytes());
                    sectionId = UUID.nameUUIDFromBytes(("classsection-" + grade + "-" + section).getBytes());
                }

                if (!schoolClassRepository.existsById(classId)) {
                    SchoolClass schoolClass = new SchoolClass(classId, grade, section, "Room " + (100 + roomCounter), 40);
                    schoolClass.setTenantId(tenantId);
                    schoolClass.setAcademicYearId(academicYearId);
                    schoolClassRepository.save(schoolClass);
                }

                if (!classSectionRepo.existsById(sectionId)) {
                    ClassSection classSection = new ClassSection();
                    classSection.setId(sectionId);
                    classSection.setTenantId(tenantId);
                    classSection.setAcademicYearId(academicYearId);
                    classSection.setGradeName(grade);
                    classSection.setSectionName(section);
                    classSection.setRoomNumber("Room " + (100 + roomCounter));
                    classSection.setTeacherId(UUID.nameUUIDFromBytes(("teacher-" + grade + "-" + section).getBytes()));
                    classSectionRepo.save(classSection);
                }

                roomCounter++;
            }
        }

        System.out.println(">> Scale Simulation Seeder -> 22 SchoolClass and ClassSection layers verified.");

        // Fetch populated classes/sections to distribute students cleanly
        List<SchoolClass> allSchoolClasses = schoolClassRepository.findAll();
        List<ClassSection> allClassSections = classSectionRepo.findAll();
        int numClasses = allSchoolClasses.size();

        Random random = new Random(42);
        int[] rollCounters = new int[numClasses];

        String[] firstNames = {
            "Aarav", "Diya", "Rohan", "Abhay", "Arnav", "Neha", "Kabir", "Ishaan", "Pooja", "Vikram",
            "Rahul", "Priya", "Aditya", "Siddharth", "Meera", "Zara", "Dev", "Alisha", "Samir", "Riya",
            "Yash", "Tanvi", "Kunal", "Kiara", "Karan", "Nisha", "Rishi", "Sana", "Ravi", "Aria",
            "Amit", "Divya", "Sanjay", "Kavita", "Vijay", "Asha", "Rajesh", "Maya", "Sunil", "Geeta"
        };
        String[] lastNames = {
            "Sharma", "Patel", "Das", "Iyer", "Mehta", "Joshi", "Verma", "Rao", "Nair", "Reddy",
            "Singh", "Gupta", "Sen", "Bose", "Choudhury", "Pillai", "Mishra", "Kumar", "Chawla", "Bhasin"
        };

        // 2. Loop to insert 500 distinct Student entities distributed across SchoolClass instances
        for (int i = 1; i <= 500; i++) {
            int classIndex = (i - 1) % numClasses;
            SchoolClass schoolClass = allSchoolClasses.get(classIndex);
            
            ClassSection classSection = allClassSections.stream()
                .filter(cs -> cs.getGradeName().equals(schoolClass.getGradeLevel()) && cs.getSectionName().equals(schoolClass.getSectionName()))
                .findFirst()
                .orElse(allClassSections.get(0));
                
            rollCounters[classIndex]++;
            int rollNumIndex = rollCounters[classIndex];
            
            String gradeShort = schoolClass.getGradeLevel().replace("Grade ", "");
            String rollNumber = gradeShort + schoolClass.getSectionName() + "-" + String.format("%02d", rollNumIndex);
            
            UUID studentId = UUID.nameUUIDFromBytes(("student-scale-" + i).getBytes());
            String firstName = firstNames[(i - 1) % firstNames.length];
            String lastName = lastNames[(i - 1) % lastNames.length];

            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                student = new Student();
                student.setId(studentId);
                student.setTenantId(tenantId);
                student.setAcademicYearId(academicYearId);
                student.setFirstName(firstName);
                student.setLastName(lastName);
                student.setRollNumber(rollNumber);
                student.setClassSection(classSection);
                student.setSchoolClass(schoolClass);
                studentRepository.save(student);
            }

            // Link matching StudentMetric with random XP bounds (0 - 1000)
            UUID metricId = UUID.nameUUIDFromBytes(("metric-scale-" + i).getBytes());
            StudentMetric metric = studentMetricRepository.findById(metricId).orElse(null);
            if (metric == null) {
                metric = new StudentMetric();
                metric.setId(metricId);
                metric.setTenantId(tenantId);
                metric.setAcademicYearId(academicYearId);
                metric.setStudent(student);
                metric.setSchoolXp(random.nextInt(1001));
                metric.setParentXp(random.nextInt(1001));
                metric.setActiveStreak(random.nextInt(15));
                studentMetricRepository.save(metric);
            }
        }

        // Initialize stable StudentMetric for Arjun Sharma if missing
        UUID arjunId = UUID.fromString("00000000-0000-0000-0000-000000000091");
        Student arjun = studentRepository.findById(arjunId).orElse(null);
        if (arjun != null) {
            StudentMetric arjunMetric = studentMetricRepository.findByStudentId(arjunId).orElse(null);
            if (arjunMetric == null) {
                arjunMetric = new StudentMetric();
                arjunMetric.setId(UUID.nameUUIDFromBytes("arjun-metric".getBytes()));
                arjunMetric.setTenantId(tenantId);
                arjunMetric.setAcademicYearId(academicYearId);
                arjunMetric.setStudent(arjun);
                arjunMetric.setSchoolXp(300);
                arjunMetric.setParentXp(100);
                arjunMetric.setActiveStreak(5);
                studentMetricRepository.save(arjunMetric);
            }
        }

        System.out.println(">> Scale Simulation Seeder -> 500 students and student metrics successfully seeded.");

        // 3. Seed robust sample of 20 Staff/User records with split roles (ADMIN and TEACHER)
        for (int i = 1; i <= 20; i++) {
            UUID userId = UUID.nameUUIDFromBytes(("staff-scale-" + i).getBytes());
            String email = "staff" + String.format("%02d", i) + "@greenwood.com";
            UserRole role = (i % 2 == 1) ? UserRole.ADMIN : UserRole.TEACHER;
            String roleName = (role == UserRole.ADMIN) ? "Admin" : "Teacher";

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                user = new User();
                user.setId(userId);
                user.setTenantId(tenantId);
                user.setAcademicYearId(academicYearId);
                user.setEmail(email);
                user.setPasswordHash(passwordEncoder.encode("GreenwoodStaffTesting2026!"));
                user.setFullName(roleName + " Staff " + i);
                user.setRole(role);
                user.setActive(true);
                userRepository.save(user);
            } else {
                user.setPasswordHash(passwordEncoder.encode("GreenwoodStaffTesting2026!"));
                userRepository.save(user);
            }
        }

        System.out.println(">> Scale Simulation Seeder -> 20 Staff/User accounts (10 ADMIN, 10 TEACHER) verified.");

        System.out.println(">> Scale Simulation Seeder -> SUCCESS.");
    }

    private void seedPilotDatabaseCredentials(UUID tenantId, UUID academicYearId) {
        System.out.println(">> Scale Simulation Seeder -> Seeding Pilot Database Credentials");
        String pilotPassword = passwordEncoder.encode(demoAdminPassword);
        
        // Helper array to seed roles easily
        String[][] pilotAccounts = {
            {"admin@greenwood.com", "ADMIN", "Admin Pilot"},
            {"teacher@greenwood.com", "TEACHER", "Teacher Pilot"},
            {"ramesh@gmail.com", "PARENT", "Ramesh Sharma"},
            {"arjun@gmail.com", "STUDENT", "Arjun Sharma"}
        };
        
        for (String[] acc : pilotAccounts) {
            String email = acc[0];
            UserRole role = UserRole.valueOf(acc[1]);
            String fullName = acc[2];
            
            if (userRepository.findByEmail(email).isEmpty()) {
                User user = new User();
                user.setId(UUID.randomUUID());
                user.setTenantId(tenantId);
                user.setAcademicYearId(academicYearId);
                user.setEmail(email);
                user.setPasswordHash(pilotPassword);
                user.setRole(role);
                user.setFullName(fullName);
                user.setActive(true);
                userRepository.save(user);
                System.out.println("Automated Seeder -> Created Pilot DB User: " + email);
            }
        }
    }
}
