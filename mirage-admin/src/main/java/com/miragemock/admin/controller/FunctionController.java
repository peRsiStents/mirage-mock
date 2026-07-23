package com.miragemock.admin.controller;

import com.miragemock.common.api.Result;
import com.miragemock.dsl.func.FunctionCatalog;
import com.miragemock.dsl.spi.FunctionDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 函数市场：函数说明 / 示例。
 */
@RestController
@RequestMapping("/api/v1/functions")
public class FunctionController {

    @GetMapping
    public Result<List<FunctionDescriptor>> list() {
        return Result.ok(FunctionCatalog.catalog());
    }
}
