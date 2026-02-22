package io.regulyn.identity.dto;

import java.util.List;

public class AssignRolesRequest {

    private List<String> roles;

    public AssignRolesRequest() {}

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
