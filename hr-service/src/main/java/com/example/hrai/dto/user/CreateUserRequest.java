package com.example.hrai.dto.user;

import com.example.hrai.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    /**
     * 登录账号，系统内必须唯一。
     */
    @NotBlank
    @Size(max = 64)
    private String username;

    /**
     * 初始登录密码，保存前会由后端加密成 passwordHash。
     */
    @NotBlank
    @Size(min = 6, max = 64)
    private String password;

    /**
     * 账号绑定的员工姓名，后续员工自助查询会用它做数据归属判断。
     */
    @NotBlank
    @Size(max = 64)
    private String employeeName;

    /**
     * 用户角色：EMPLOYEE 为普通员工，HR 为管理端账号。
     */
    @NotNull
    private UserRole role;

    /**
     * 是否启用账号；为空时默认启用。
     */
    private Boolean enabled;
}
