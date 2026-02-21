package com.teammors.server.im.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {
    private String userId;
    private String isAdmin; // "0": member, "1": admin/owner
}
