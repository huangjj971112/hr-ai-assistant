package com.example.hrai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("candidate")
public class Candidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String position;

    private int years;

    private String skills;

    private String status;

    private String phone;

    private String email;
}
