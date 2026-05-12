package com.hmdp.controller;


import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import com.hmdp.dto.Result;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.hmdp.service.IFollowService;



/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;
    @PutMapping("/{id}/{isFollow}")
    public Result putMethodName(@PathVariable Long id, @PathVariable Boolean isFollow){
        return followService.follow(id, isFollow);
    }
    @GetMapping("/or/not/{id}")
    public Result getMethodName(@PathVariable Long id){
        Boolean isFollow = followService.isFollow(id);  
        return Result.ok(isFollow);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id){
        return followService.followCommons(id);
    }
}
