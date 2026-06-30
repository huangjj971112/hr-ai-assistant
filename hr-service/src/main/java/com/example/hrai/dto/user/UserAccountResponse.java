package com.example.hrai.dto.user;

import com.example.hrai.entity.UserRole;

public record UserAccountResponse(
        /**
         * 用户表主键。
         */
        Long id,
        /**
         * 登录账号。
         */
        String username,
        /**
         * 账号绑定的员工姓名。
         */
        String employeeName,
        /**
         * 当前账号角色。
         */
        UserRole role,
        /**
         * 账号是否可登录。
         */
        Boolean enabled,
        /**
         * 员工档案处理结果：EXISTING 表示绑定已有档案，CREATED 表示自动创建新档案，SKIPPED 表示无需处理。
         */
        String employeeProfileStatus,
        /**
         * 给前端展示的用户管理提示语。
         */
        String message
) {
}
