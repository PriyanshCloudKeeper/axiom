package com.learnhai.scim.model.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ScimResource {
    private List<String> schemas = new ArrayList<>();
    private String id;
    private String externalId;
    private ScimUser.Meta meta = new ScimUser.Meta();
}