package com.hmdp.dto;

import lombok.Data;

@Data
public class UserDTO {
//    private Long userId;
    private Long id; // 这个字段和user要保持一致。
    private String nickName;
    private String icon;
}
