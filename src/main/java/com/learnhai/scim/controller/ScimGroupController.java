package com.learnhai.scim.controller;

import com.learnhai.scim.model.scim.ScimGroup;
import com.learnhai.scim.service.ScimGroupService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/scim/v2/Groups")
@Slf4j
public class ScimGroupController {

    private final ScimGroupService scimGroupService;

    @Autowired
    public ScimGroupController(ScimGroupService scimGroupService) {
        this.scimGroupService = scimGroupService;
    }

    @PostMapping
    public ResponseEntity<ScimGroup> createGroup(@Valid @RequestBody ScimGroup scimGroup) {
        log.info("SCIM createGroup request received for displayName: {}", scimGroup.getDisplayName());
        ScimGroup createdGroup = scimGroupService.createGroup(scimGroup);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdGroup.getId())
                .toUri();
        log.info("SCIM group created with ID: {}", createdGroup.getId());
        return ResponseEntity.created(location).body(createdGroup);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScimGroup> getGroup(@PathVariable String id) {
        log.info("SCIM getGroup request received for ID: {}", id);
        return scimGroupService.getGroupById(id)
                .map(group -> {
                     log.info("SCIM group found with ID: {}", id);
                    return ResponseEntity.ok(group);
                })
                .orElseGet(() -> {
                    log.warn("SCIM group not found with ID: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScimGroup> replaceGroup(@PathVariable String id,
                                                  @Valid @RequestBody ScimGroup scimGroup) {
        log.info("SCIM replaceGroup (PUT) request received for ID: {}", id);
        ScimGroup updatedGroup = scimGroupService.replaceGroup(id, scimGroup);
        log.info("SCIM group updated with ID: {}", id);
        return ResponseEntity.ok(updatedGroup);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ScimGroup> patchGroup(@PathVariable String id,
                                                @RequestBody Map<String, Object> patchRequest) {
        log.info("SCIM patchGroup request received for ID: {}", id);
        ScimGroup updatedGroup = scimGroupService.patchGroup(id, patchRequest);
        log.info("SCIM group patched with ID: {}", id);
        return ResponseEntity.ok(updatedGroup);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        log.info("SCIM deleteGroup request received for ID: {}", id);
        scimGroupService.deleteGroup(id);
        log.info("SCIM group deleted with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> findGroups(
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "startIndex", defaultValue = "1") int startIndex,
            @RequestParam(name = "count", defaultValue = "100") int count) {
        log.info("SCIM findGroups request received. Filter: '{}', StartIndex: {}, Count: {}", filter, startIndex, count);
        int effectiveCount = Math.min(count, 200);

        Map<String, Object> listResponse = scimGroupService.getGroups(startIndex, effectiveCount, filter);
        return ResponseEntity.ok(listResponse);
    }
}