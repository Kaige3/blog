package com.kaige.controller.web;

import com.kaige.entity.Comment;
import com.kaige.entity.Result;
import com.kaige.enums.CommentOpenStateEnum;
import com.kaige.service.CommentService;
import com.kaige.utils.TokenAndPasswordVerify;
import com.kaige.utils.comment.CommentUtils;
import org.babyfish.jimmer.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.List;

@RestController
public class CommentController {

    @Autowired
    CommentUtils commentUtils;
    @Autowired
    private CommentService commentService;

    @GetMapping("/comments")
    public Result comments(@RequestParam Integer page,
                           @RequestParam(defaultValue = "") Long blogId,
                           @RequestParam(defaultValue = "1") Integer pageNum,
                           @RequestParam(defaultValue = "10")Integer pageSize,
                           @RequestHeader(value = "Authorization",defaultValue = "") String jwt){
        CommentOpenStateEnum commentOpenStateEnum = commentUtils.judgeCommentState(page, blogId);
        switch (commentOpenStateEnum){
            case NOT_FOUND -> {
                return Result.create(404,"该博客不存在");
            }
            case CLOSE -> {
                return Result.create(403,"评论功能已关闭");
            }
            case PASSWORD -> TokenAndPasswordVerify.judgeTokenAndPasswordIsOK(jwt,blogId);
        }
//        该页面所有评论的 数量
        Integer addComment = commentService.getcountByPageAndIsPublished(page,blogId,null);
//        改页面所有公布评论的 数量
        List<Comment> commentPage = commentService.getPageCommentList(page,blogId);
        return Result.ok("你好了",commentPage);
    }


}
