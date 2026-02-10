package com.regulyn.employee;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.employee.config.TestSecurityConfig;
import com.regulyn.employee.dto.*;
import com.regulyn.employee.dto.EmployeeResponse;
import com.regulyn.employee.dto.HRPurposeResponse;
import com.regulyn.employee.dto.EmployeeRequestResponse;
import com.regulyn.employee.dto.ApproveRequestRequest;
import java.util.Collections;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.employee.model.Employee;
import com.regulyn.employee.model.Employee.EmployeeStatus;
import com.regulyn.employee.model.EmployeeDataRecord;
import com.regulyn.employee.model.HRPurpose;
import com.regulyn.employee.repository.EmployeeDataRecordRepository;
import com.regulyn.employee.repository.EmployeeRepository;
import com.regulyn.employee.repository.HRPurposeRepository;
import com.regulyn.employee.service.EmployeeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class EmployeeServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.task.scheduling.enabled", () -> false);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private HRPurposeRepository hrPurposeRepository;

    @Autowired
    private EmployeeDataRecordRepository employeeDataRecordRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext ctx = new TenantContext(); ctx.setTenantId(tenantId); ctx.setUserId(UUID.randomUUID()); ctx.setRoles(Collections.singleton("ROLE_USER")); TenantContextHolder.setContext(ctx);
    }

    @AfterEach
    void tearDown() {
        employeeDataRecordRepository.deleteAll();
        employeeRepository.deleteAll();
        hrPurposeRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Should create employee and search by name")
    void testCreateEmployeeAndSearchByName() {
        // Given
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setEmployeeRef("EMP001");
        request.setFullName("John Doe");
        request.setEmail("john.doe@example.com");
        request.setDepartment("Engineering");

        // When
        EmployeeResponse createdResp = employeeService.createEmployee(request);
        Employee created = employeeRepository.findById(createdResp.getEmployeeId()).orElseThrow();

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getEmployeeId()).isNotNull();
        assertThat(created.getTenantId()).isEqualTo(tenantId);
        assertThat(created.getEmployeeRef()).isEqualTo("EMP001");
        assertThat(created.getFullName()).isEqualTo("John Doe");
        assertThat(created.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(created.getDepartment()).isEqualTo("Engineering");
        assertThat(created.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);

        // Search by name
        List<Employee> found = employeeRepository.findByTenantIdAndFullNameContainingIgnoreCase(
                tenantId, "john");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Should search employees by status")
    void testSearchEmployeesByStatus() {
        // Given
        CreateEmployeeRequest request1 = new CreateEmployeeRequest();
        request1.setEmployeeRef("EMP001");
        request1.setFullName("Alice Smith");
        request1.setEmail("alice@example.com");
        request1.setDepartment("HR");

        CreateEmployeeRequest request2 = new CreateEmployeeRequest();
        request2.setEmployeeRef("EMP002");
        request2.setFullName("Bob Jones");
        request2.setEmail("bob@example.com");
        request2.setDepartment("Finance");

        // When
        employeeService.createEmployee(request1);
        EmployeeResponse emp2Resp = employeeService.createEmployee(request2);

        // Deactivate one employee
        Employee emp2 = employeeRepository.findById(emp2Resp.getEmployeeId()).orElseThrow();
        emp2.setStatus(EmployeeStatus.INACTIVE);
        employeeRepository.save(emp2);

        // Then
        List<Employee> activeEmployees = employeeRepository.findByTenantIdAndStatus(
                tenantId, EmployeeStatus.ACTIVE);
        assertThat(activeEmployees).hasSize(1);
        assertThat(activeEmployees.get(0).getFullName()).isEqualTo("Alice Smith");

        List<Employee> inactiveEmployees = employeeRepository.findByTenantIdAndStatus(
                tenantId, EmployeeStatus.INACTIVE);
        assertThat(inactiveEmployees).hasSize(1);
        assertThat(inactiveEmployees.get(0).getFullName()).isEqualTo("Bob Jones");
    }

    @Test
    @DisplayName("Should create HR purpose and list purposes")
    void testCreateHRPurposeAndList() {
        // Given
        CreateHRPurposeRequest request = new CreateHRPurposeRequest();
        request.setPurposeKey(HRPurpose.PurposeKey.PERFORMANCE);
        request.setLawfulBasis(HRPurpose.LawfulBasis.LEGITIMATE_INTERESTS);
        request.setDescription("Annual performance reviews");
        request.setRetentionDays(1825); // 5 years

        // When
        HRPurposeResponse createdResp = employeeService.createHRPurpose(request);
        HRPurpose created = hrPurposeRepository.findById(createdResp.getHrPurposeId()).orElseThrow();

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getHrPurposeId()).isNotNull();
        assertThat(created.getTenantId()).isEqualTo(tenantId);
        assertThat(created.getPurposeKey()).isEqualTo(HRPurpose.PurposeKey.PERFORMANCE);
        assertThat(created.getLawfulBasis()).isEqualTo(HRPurpose.LawfulBasis.LEGITIMATE_INTERESTS);
        assertThat(created.getRetentionDays()).isEqualTo(1825);

        // List purposes
        List<HRPurpose> purposes = hrPurposeRepository.findByTenantId(tenantId);
        assertThat(purposes).hasSize(1);
        assertThat(purposes.get(0).getPurposeKey()).isEqualTo(HRPurpose.PurposeKey.PERFORMANCE);
    }

    @Test
    @DisplayName("Should create data record with filters")
    void testCreateDataRecordWithFilters() {
        // Given - Create employee first
        CreateEmployeeRequest empRequest = new CreateEmployeeRequest();
        empRequest.setEmployeeRef("EMP001");
        empRequest.setFullName("Jane Doe");
        empRequest.setEmail("jane@example.com");
        empRequest.setDepartment("Sales");
        EmployeeResponse employee = employeeService.createEmployee(empRequest);

        // Create HR purpose
        CreateHRPurposeRequest purposeRequest = new CreateHRPurposeRequest();
        purposeRequest.setPurposeKey(HRPurpose.PurposeKey.PAYROLL);
        purposeRequest.setLawfulBasis(HRPurpose.LawfulBasis.LEGAL_OBLIGATION);
        purposeRequest.setDescription("Salary and tax records");
        purposeRequest.setRetentionDays(2555); // 7 years
        HRPurposeResponse purpose = employeeService.createHRPurpose(purposeRequest);

        // Create data record
        CreateEmployeeDataRecordRequest dataRequest = new CreateEmployeeDataRecordRequest();
        dataRequest.setEmployeeId(employee.getEmployeeId());
        dataRequest.setHrPurposeId(purpose.getHrPurposeId());
        dataRequest.setDataCategory(EmployeeDataRecord.DataCategory.FINANCIAL);
        dataRequest.setMetadata(Map.of("dataType", "Salary", "amount", 75000, "currency", "USD"));

        // When
        EmployeeDataRecordResponse createdResp = employeeService.createEmployeeDataRecord(dataRequest);
        EmployeeDataRecord created = employeeDataRecordRepository.findById(createdResp.getRecordId()).orElseThrow();

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getRecordId()).isNotNull();
        assertThat(created.getTenantId()).isEqualTo(tenantId);
        assertThat(created.getEmployeeId()).isEqualTo(employee.getEmployeeId());
        assertThat(created.getHrPurposeId()).isEqualTo(purpose.getHrPurposeId());
        assertThat(created.getDataCategory()).isEqualTo(EmployeeDataRecord.DataCategory.FINANCIAL);

        // Filter by employee
        List<EmployeeDataRecord> employeeRecords = 
                employeeDataRecordRepository.findByTenantIdAndEmployeeId(tenantId, employee.getEmployeeId());
        assertThat(employeeRecords).hasSize(1);

        // Filter by purpose
        List<EmployeeDataRecord> purposeRecords = 
                employeeDataRecordRepository.findByTenantIdAndHrPurposeId(tenantId, purpose.getHrPurposeId());
        assertThat(purposeRecords).hasSize(1);

        // Filter by category
        List<EmployeeDataRecord> categoryRecords = 
                employeeDataRecordRepository.findByTenantIdAndDataCategory(tenantId, EmployeeDataRecord.DataCategory.FINANCIAL);
        assertThat(categoryRecords).hasSize(1);
    }

    @Test
    @DisplayName("Should handle multiple data records for same employee")
    void testMultipleDataRecordsForEmployee() {
        // Given - Create employee
        CreateEmployeeRequest empRequest = new CreateEmployeeRequest();
        empRequest.setEmployeeRef("EMP001");
        empRequest.setFullName("Tom Wilson");
        empRequest.setEmail("tom@example.com");
        empRequest.setDepartment("IT");
        EmployeeResponse employee = employeeService.createEmployee(empRequest);

        // Create multiple purposes
        CreateHRPurposeRequest purpose1Request = new CreateHRPurposeRequest();
        purpose1Request.setPurposeKey(HRPurpose.PurposeKey.RECRUITMENT);
        purpose1Request.setLawfulBasis(HRPurpose.LawfulBasis.CONSENT);
        purpose1Request.setDescription("Recruitment data");
        purpose1Request.setRetentionDays(365);
        HRPurposeResponse purpose1 = employeeService.createHRPurpose(purpose1Request);

        CreateHRPurposeRequest purpose2Request = new CreateHRPurposeRequest();
        purpose2Request.setPurposeKey(HRPurpose.PurposeKey.OTHER);
        purpose2Request.setLawfulBasis(HRPurpose.LawfulBasis.LEGITIMATE_INTERESTS);
        purpose2Request.setDescription("Training records");
        purpose2Request.setRetentionDays(730);
        HRPurposeResponse purpose2 = employeeService.createHRPurpose(purpose2Request);

        // Create multiple records
        CreateEmployeeDataRecordRequest record1 = new CreateEmployeeDataRecordRequest();
        record1.setEmployeeId(employee.getEmployeeId());
        record1.setHrPurposeId(purpose1.getHrPurposeId());
        record1.setDataCategory(EmployeeDataRecord.DataCategory.PII);
        record1.setMetadata(Map.of("type", "Resume", "filename", "resume.pdf"));

        CreateEmployeeDataRecordRequest record2 = new CreateEmployeeDataRecordRequest();
        record2.setEmployeeId(employee.getEmployeeId());
        record2.setHrPurposeId(purpose2.getHrPurposeId());
        record2.setDataCategory(EmployeeDataRecord.DataCategory.EMPLOYEE_DATA);
        record2.setMetadata(Map.of("cert", "AWS Solutions Architect"));

        // When
        employeeService.createEmployeeDataRecord(record1);
        employeeService.createEmployeeDataRecord(record2);

        // Then
        List<EmployeeDataRecord> allRecords = 
                employeeDataRecordRepository.findByTenantIdAndEmployeeId(tenantId, employee.getEmployeeId());
        assertThat(allRecords).hasSize(2);
        assertThat(allRecords).extracting(EmployeeDataRecord::getDataCategory)
                .containsExactlyInAnyOrder(EmployeeDataRecord.DataCategory.PII, EmployeeDataRecord.DataCategory.EMPLOYEE_DATA);
    }
}



