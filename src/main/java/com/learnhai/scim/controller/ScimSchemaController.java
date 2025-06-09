package com.learnhai.scim.controller;

import com.learnhai.scim.model.scim.ScimUser; 
import com.learnhai.scim.model.scim.ScimGroup; 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/scim/v2")
public class ScimSchemaController {

    private final String scimBaseUrl;

    public ScimSchemaController(@Value("${scim.base-url:${server.servlet.context-path:}}") String scimBaseUrl) {
        this.scimBaseUrl = "/".equals(scimBaseUrl) ? "" : scimBaseUrl;
    }

    private static final String SCHEMA_LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    private static final String SCHEMA_RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";
    private static final String SCHEMA_SERVICE_PROVIDER_CONFIG = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";


    @GetMapping("/ServiceProviderConfig")
    public ResponseEntity<Map<String, Object>> getServiceProviderConfig() {
        Map<String, Object> config = new LinkedHashMap<>(); 
        config.put("schemas", List.of(SCHEMA_SERVICE_PROVIDER_CONFIG));
        config.put("documentationUri", "https://example.com/scim/docs"); 

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("supported", true);
        config.put("patch", patch);

        Map<String, Object> bulk = new LinkedHashMap<>();
        bulk.put("supported", false); 
        bulk.put("maxOperations", 1000); 
        bulk.put("maxPayloadSize", 1048576); 
        config.put("bulk", bulk);

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("supported", true); 
        filter.put("maxResults", 200); 
        config.put("filter", filter);

        Map<String, Object> changePassword = new LinkedHashMap<>();
        changePassword.put("supported", false); 
        config.put("changePassword", changePassword);

        Map<String, Object> sort = new LinkedHashMap<>();
        sort.put("supported", false); 
        config.put("sort", sort);

        Map<String, Object> etag = new LinkedHashMap<>();
        etag.put("supported", false); 
        config.put("etag", etag);

        List<Map<String, Object>> authSchemes = new ArrayList<>();
        Map<String, Object> oauthBearer = new LinkedHashMap<>();
        oauthBearer.put("type", "oauthbearertoken");
        oauthBearer.put("name", "OAuth Bearer Token");
        oauthBearer.put("description", "OAuth 2.0 Bearer Token for SCIM authentication.");
        authSchemes.add(oauthBearer);
        config.put("authenticationSchemes", authSchemes);

        return ResponseEntity.ok(config);
    }

    @GetMapping("/ResourceTypes")
    public ResponseEntity<Map<String, Object>> getResourceTypes() {
        List<Map<String, Object>> resourceTypes = Arrays.asList(
                createUserResourceType(),
                createGroupResourceType()
        );

        Map<String, Object> listResponse = new LinkedHashMap<>();
        listResponse.put("schemas", List.of(SCHEMA_LIST_RESPONSE));
        listResponse.put("totalResults", resourceTypes.size());
        listResponse.put("startIndex", 1);
        listResponse.put("itemsPerPage", resourceTypes.size());
        listResponse.put("Resources", resourceTypes);

        return ResponseEntity.ok(listResponse);
    }

    @GetMapping("/ResourceTypes/{resourceTypeName}")
    public ResponseEntity<Map<String, Object>> getResourceType(@PathVariable String resourceTypeName) {
        if ("User".equalsIgnoreCase(resourceTypeName)) {
            return ResponseEntity.ok(createUserResourceType());
        } else if ("Group".equalsIgnoreCase(resourceTypeName)) {
            return ResponseEntity.ok(createGroupResourceType());
        }
        return ResponseEntity.notFound().build();
    }


    @GetMapping("/Schemas")
    public ResponseEntity<Map<String, Object>> getSchemas() {
        List<Map<String, Object>> schemas = Arrays.asList(
                getUserSchemaDefinition(),
                getGroupSchemaDefinition(),
                getEnterpriseUserSchemaDefinition() 
        );

        Map<String, Object> listResponse = new LinkedHashMap<>();
        listResponse.put("schemas", List.of(SCHEMA_LIST_RESPONSE));
        listResponse.put("totalResults", schemas.size());
        listResponse.put("startIndex", 1);
        listResponse.put("itemsPerPage", schemas.size());
        listResponse.put("Resources", schemas);
        return ResponseEntity.ok(listResponse);
    }

    @GetMapping("/Schemas/{schemaUrn}")
    public ResponseEntity<Map<String, Object>> getSchemaByUrn(@PathVariable String schemaUrn) {
        if (ScimUser.SCHEMA_CORE_USER.equalsIgnoreCase(schemaUrn)) {
            return ResponseEntity.ok(getUserSchemaDefinition());
        } else if (ScimGroup.SCHEMA_CORE_GROUP.equalsIgnoreCase(schemaUrn)) {
            return ResponseEntity.ok(getGroupSchemaDefinition());
        } else if (ScimUser.SCHEMA_ENTERPRISE_USER.equalsIgnoreCase(schemaUrn)) {
            return ResponseEntity.ok(getEnterpriseUserSchemaDefinition());
        }
        return ResponseEntity.notFound().build();
    }


    private Map<String, Object> createUserResourceType() {
        Map<String, Object> userType = new LinkedHashMap<>();
        userType.put("schemas", List.of(SCHEMA_RESOURCE_TYPE));
        userType.put("id", "User");
        userType.put("name", "User");
        userType.put("description", "User Account");
        userType.put("endpoint", scimBaseUrl + "/scim/v2/Users");
        userType.put("schema", ScimUser.SCHEMA_CORE_USER);
        List<Map<String,Object>> schemaExtensions = new ArrayList<>();
        Map<String,Object> enterpriseExt = new LinkedHashMap<>();
        enterpriseExt.put("schema", ScimUser.SCHEMA_ENTERPRISE_USER);
        enterpriseExt.put("required", false);
        schemaExtensions.add(enterpriseExt);
        userType.put("schemaExtensions", schemaExtensions);
        
        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("location", scimBaseUrl + "/scim/v2/ResourceTypes/User");
        meta.put("resourceType", "ResourceType");
        userType.put("meta", meta);
        return userType;
    }

    private Map<String, Object> createGroupResourceType() {
        Map<String, Object> groupType = new LinkedHashMap<>();
        groupType.put("schemas", List.of(SCHEMA_RESOURCE_TYPE));
        groupType.put("id", "Group");
        groupType.put("name", "Group");
        groupType.put("description", "Group");
        groupType.put("endpoint", scimBaseUrl + "/scim/v2/Groups");
        groupType.put("schema", ScimGroup.SCHEMA_CORE_GROUP);
        
        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("location", scimBaseUrl + "/scim/v2/ResourceTypes/Group");
        meta.put("resourceType", "ResourceType");
        groupType.put("meta", meta);
        return groupType;
    }


    private Map<String, Object> getUserSchemaDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("id", ScimUser.SCHEMA_CORE_USER);
        schema.put("name", "User");
        schema.put("description", "Core User Account");
        schema.put("attributes", getUserAttributes());
        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "Schema");
        meta.put("location", scimBaseUrl + "/scim/v2/Schemas/" + ScimUser.SCHEMA_CORE_USER);
        schema.put("meta", meta);
        return schema;
    }

    private Map<String, Object> getGroupSchemaDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("id", ScimGroup.SCHEMA_CORE_GROUP);
        schema.put("name", "Group");
        schema.put("description", "Core Group");
        schema.put("attributes", getGroupAttributes());
        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "Schema");
        meta.put("location", scimBaseUrl + "/scim/v2/Schemas/" + ScimGroup.SCHEMA_CORE_GROUP);
        schema.put("meta", meta);
        return schema;
    }
    
    private Map<String, Object> getEnterpriseUserSchemaDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("id", ScimUser.SCHEMA_ENTERPRISE_USER);
        schema.put("name", "EnterpriseUser");
        schema.put("description", "Enterprise User Extension");
        schema.put("attributes", getEnterpriseUserAttributes());
        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "Schema");
        meta.put("location", scimBaseUrl + "/scim/v2/Schemas/" + ScimUser.SCHEMA_ENTERPRISE_USER);
        schema.put("meta", meta);
        return schema;
    }


    private List<Map<String, Object>> getUserAttributes() {
        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(createAttribute("userName", "string", false, true, "server", "readWrite", "default", "Unique identifier for the User."));
        attributes.add(createComplexAttribute("name", false, "readWrite", "default", "The components of the user's real name.", List.of(
                createAttribute("formatted", "string", false, false, "none", "readWrite", "default", "The full name, including all middle names, titles, and suffixes."),
                createAttribute("familyName", "string", false, false, "none", "readWrite", "default", "The family name of the User."),
                createAttribute("givenName", "string", false, false, "none", "readWrite", "default", "The given name of the User."),
                createAttribute("middleName", "string", false, false, "none", "readWrite", "default", "The middle name(s) of the User."),
                createAttribute("honorificPrefix", "string", false, false, "none", "readWrite", "default", "The honorific prefix(es) of the User."),
                createAttribute("honorificSuffix", "string", false, false, "none", "readWrite", "default", "The honorific suffix(es) of the User.")
        )));
        attributes.add(createAttribute("displayName", "string", false, false, "none", "readWrite", "default", "The name of the User, suitable for display."));
        attributes.add(createAttribute("nickName", "string", false, false, "none", "readWrite", "default", "The casual way to address the user."));
        attributes.add(createAttribute("profileUrl", "reference", false, false, "none", "readWrite", "default", "A URI that is a uniform resource locator (URL) that points to a location representing the User's online profile."));
        attributes.add(createAttribute("title", "string", false, false, "none", "readWrite", "default", "The user's title, such as \"Vice President.\""));
        attributes.add(createAttribute("userType", "string", false, false, "none", "readWrite", "default", "Used to identify the organization SCIM attribute schema."));
        attributes.add(createAttribute("preferredLanguage", "string", false, false, "none", "readWrite", "default", "Indicates the User's preferred written or spoken language."));
        attributes.add(createAttribute("locale", "string", false, false, "none", "readWrite", "default", "Used to indicate the User's default location for purposes of localizing items such as currency, date time format, or numerical representations."));
        attributes.add(createAttribute("timezone", "string", false, false, "none", "readWrite", "default", "The User's time zone, in IANA Time Zone database format."));
        attributes.add(createAttribute("active", "boolean", false, false, "none", "readWrite", "default", "A Boolean value indicating the User's administrative status."));
        attributes.add(createComplexAttribute("emails", true, "readWrite", "default", "Email addresses for the user.", List.of(
                createAttribute("value", "string", false, false, "none", "readWrite", "default", "Email address value."),
                createAttribute("display", "string", false, false, "none", "readWrite", "default", "A human-readable name, primarily used for display purposes."),
                createAttribute("type", "string", false, false, "none", "readWrite", "default", "A label indicating the attribute's function, e.g., 'work' or 'home'."),
                createAttribute("primary", "boolean", false, false, "none", "readWrite", "default", "A Boolean value indicating the 'primary' or preferred attribute value for this attribute.")
        )));
        attributes.add(createComplexAttribute("phoneNumbers", true, "readWrite", "default", "Phone numbers for the User.", List.of(
                createAttribute("value", "string", false, false, "none", "readWrite", "default", "Phone number value."),
                createAttribute("display", "string", false, false, "none", "readWrite", "default", "A human-readable name, primarily used for display purposes."),
                createAttribute("type", "string", false, false, "none", "readWrite", "default", "A label indicating the attribute's function, e.g., 'work', 'home', 'mobile'."),
                createAttribute("primary", "boolean", false, false, "none", "readWrite", "default", "A Boolean value indicating the 'primary' or preferred attribute value for this attribute.")
        )));
        attributes.add(createAttribute("password", "string", false, false, "none", "writeOnly", "never", "The User's password. This attribute is write-only, and will never be returned in a response."));
        
        attributes.add(createComplexAttribute(
                "groups",     
                true,          
                "readOnly",    
                "default",
                "A list of groups to which the user belongs.",
                List.of(
                        createAttribute("value", "string", false, false, "none", "readOnly", "default", "The identifier of the User's group."),
                        createAttribute("display", "string", false, false, "none", "readOnly", "default", "A human-readable name for the User's group."),
                        createAttribute("$ref", "reference", false, false, "none", "readOnly", "default", "The URI of the corresponding 'Group' resource."),
                        createAttribute("type", "string", false, false, "none", "readOnly", "default", "A label indicating the type of group membership, e.g., 'direct'.")
                )
        ));
        return attributes;
    }

    private List<Map<String, Object>> getGroupAttributes() {
        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(createAttribute("displayName", "string", false, true, "none", "readWrite", "default", "A human-readable name for the Group. REQUIRED."));
        attributes.add(createComplexAttribute("members", true, "readWrite", "default", "A list of members of the Group.", List.of(
                createAttribute("value", "string", false, false, "none", "readWrite", "default", "Identifier of the member of this Group."),
                createAttribute("display", "string", false, false, "none", "readOnly", "default", "Human-readable name of the member display."),
                createAttribute("$ref", "reference", false, false, "none", "readOnly", "default", "The URI of the corresponding 'User' or 'Group' resource."),
                createAttribute("type", "string", false, false, "none", "readWrite", "default", "A label indicating the type of resource, e.g., 'User' or 'Group'.")
        )));
        return attributes;
    }
    
    private List<Map<String, Object>> getEnterpriseUserAttributes() {
        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(createAttribute("employeeNumber", "string", false, false, "none", "readWrite", "default", "Numeric or alphanumeric identifier assigned to a person."));
        attributes.add(createAttribute("costCenter", "string", false, false, "none", "readWrite", "default", "Identifies the name of a cost center."));
        attributes.add(createAttribute("organization", "string", false, false, "none", "readWrite", "default", "Identifies the name of an organization."));
        attributes.add(createAttribute("division", "string", false, false, "none", "readWrite", "default", "Identifies the name of a division."));
        attributes.add(createAttribute("department", "string", false, false, "none", "readWrite", "default", "Identifies the name of a department."));
        attributes.add(createComplexAttribute("manager", false, "readWrite", "default", "The User's manager.", List.of(
            createAttribute("value", "string", false, false, "none", "readWrite", "default", "The id of the SCIM resource representing the User's manager."),
            createAttribute("$ref", "reference", false, false, "none", "readOnly", "default", "The URI of the SCIM resource representing the User's manager."),
            createAttribute("displayName", "string", false, false, "none", "readOnly", "default", "The displayName of the User's manager.")
        )));
        return attributes;
    }


    private Map<String, Object> createAttribute(String name, String type, boolean multiValued,
                                                boolean required, String uniqueness, String mutability,
                                                String returned, String description) {
        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("name", name);
        attr.put("type", type);
        attr.put("multiValued", multiValued);
        attr.put("description", description);
        attr.put("required", required);
        attr.put("caseExact", "reference".equals(type) || "binary".equals(type)); 
        attr.put("mutability", mutability);
        attr.put("returned", returned);
        attr.put("uniqueness", uniqueness); 
        return attr;
    }

    private Map<String, Object> createComplexAttribute(String name, boolean multiValued, String mutability,
                                                       String returned, String description, List<Map<String,Object>> subAttributes) {
         Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("name", name);
        attr.put("type", "complex");
        attr.put("multiValued", multiValued);
        attr.put("description", description);
        attr.put("required", false); 
        attr.put("mutability", mutability);
        attr.put("returned", returned);
        attr.put("subAttributes", subAttributes);
        return attr;
    }
}