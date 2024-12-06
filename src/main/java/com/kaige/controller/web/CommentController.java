package com.kaige.controller.web;

import com.kaige.entity.Comment;
import com.kaige.entity.Result;
import com.kaige.entity.dto.CommentInput;
import com.kaige.enums.CommentOpenStateEnum;
import com.kaige.service.CommentService;
import com.kaige.utils.StringUtils;
import com.kaige.utils.TokenAndPasswordVerify;
import com.kaige.utils.comment.CommentUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.babyfish.jimmer.Page;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;

@RestController
public class CommentController {

    @Autowired
    CommentUtils commentUtils;
    @Autowired
    private CommentService commentService;

    /**
     * 根据页面分页 查询评论列表
     * @param page 页面分类 0普通文章，1关于我的页面 2友链页面
     * @param blogId 当page为0时，需要blogId
     * @param pageNum 页码
     * @param pageSize 每页个数
     * @param jwt token
     * @return
     */
//    TODO  应该是有一个bug , pageNum 没有用到
    @GetMapping("/comments")
    public Result comments(@RequestParam Integer page,
                           @RequestParam(defaultValue = "") BigInteger blogId,
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
        Integer allComment = commentService.getcountByPageAndIsPublished(page,blogId,null);
//         改页面公开评论的 数量
        Integer i = commentService.getcountByPageAndIsPublished(page, blogId, true);
//        改页面所有公布评论的 数量
        List<Comment> commentPage = commentService.getPageCommentList(page,blogId);
//      计算总页数
        int totalItems = commentPage.size(); // 总数据条数
        int totalPages = totalItems / pageSize; // 初步计算总页数
//      如果有剩余的数据（总数据不能被 pageSize 整除），则加一页
        if (totalItems % pageSize != 0) {
            totalPages ++;
        }
//        分页好的数据
        Page<Comment> commentPage2 = new Page<>(commentPage, commentPage.size(),totalPages);
        HashMap<String, Object> map = new HashMap<>(8);
        map.put("allComment",allComment);
        map.put("closeComment",allComment - i);
        map.put("comments",commentPage2);
        return Result.ok("获取成功",map);
    }

    @PostMapping("/comment")
    public Result postComment(@RequestBody CommentInput comment,
                              HttpServletRequest request,
                              @RequestHeader(value = "Authorization",defaultValue = "") String jwt){
        /**
         * 评论内容的合法性校验优先，因为它直接决定了评论是否有效。
         * 如果内容本身不合法，后续的状态判断和身份验证等操作就没有意义。
         * 因此，先进行内容合法性校验，可以避免不必要的资源浪费，并保证只有在合法的评论内容基础上，
         * 才进一步进行是否允许评论的状态判断、父评论关联等操作。
         *
         * 这个设计将基础的校验放在前面，而将开放状态的判断放在后面，
         * 是为了逻辑清晰、性能优化和更高效地处理评论请求。
         */
        //        校验评论 内容是否合法
        if(StringUtils.isEmpty(comment.getContent()) || comment.getContent().length() > 250 || comment.getPage() == null){
            return Result.error("参数有误");
        }
        //        是否访客的评论
        boolean isVisitorComment = false;
        //        父评论
        Comment parentComment = null;
        //        对于有指定 父评论的评论，应该已父评论为准，只判断页面 可能会绕过 “评论开启状态检测
        if(comment.getParentCommentId() != null){
        //            当前评论为子评论,继承父评论所属页面 和 文章id
            parentComment = commentService.getCommentById(comment.getParentCommentId());
            Integer page = parentComment.page();
            BigInteger blogId = page == 0 ? parentComment.blogId(): null;
            comment.setPage(page);
            comment.setBlogId(blogId);
        }else {
        //            当前评论为父评论,非文章页面评论
            if (comment.getPage() != 0){
                comment.setPage(null);
            }
        }
//      判断是否可评论
        commentUtils.judgeCommentState(comment.getPage(),comment.getBlogId());
        return null;
    }


}
