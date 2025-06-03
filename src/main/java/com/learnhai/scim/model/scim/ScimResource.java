package com.learnhai.scim.model.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ScimResource {
    private List<String> schemas;
    private String id;
    private String externalId;
    private ScimUser.Meta meta; // Using ScimUser.Meta for now, could be a shared Meta

    public ScimResource(List<String> schemas) {
        this.schemas = schemas;
        this.meta = new ScimUser.Meta(); // Initialize meta
    }
}