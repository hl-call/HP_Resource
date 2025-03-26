package com.hooya.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private boolean success;

    private int code;

    private String msg;

    private T data;

    public Result(int code, T data, String msg) {
        this.success = 200 == code;
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
}
