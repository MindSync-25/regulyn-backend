package io.regulyn.identity.dto;

public class CreateTenantRequest {
    private String name;
    private String planCode;

    public CreateTenantRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }
}
