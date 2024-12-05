package com.kaige.entity;

import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.*;

import javax.validation.constraints.Null;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for table "comment"
 */
@Entity
@Table(name = "comment")
public interface Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY
    )
    BigInteger id();

    /**
     * 昵称
     */
    String nickname();

    /**
     * 邮箱
     */
    String email();

    /**
     * 评论内容
     */
    String content();

    /**
     * 头像路径
     */
    String avatar();

    /**
     * 评论时间
     */
    @Null
    LocalDateTime createTime();

    /**
     * 评论者的ip地址
     */
    @Null
    String ip();

    /**
     * 公开或回收站
     */
    @Column(name = "is_published")
    Boolean Published();

    /**
     * 博主回复
     */
    Boolean isAdminComment();

    /**
     * 0普通文章 1关于我的页面 2友链页面
     */
    Integer page();

    /**
     * 接收邮件提醒
     */
    Boolean isNotice();

    /**
     * 所属的文章
     */
    @Nullable
    BigInteger blogId();

//    @Null
//    BigInteger parentCommentId();

    /**
     * 父评论
     */
    @Nullable
    @ManyToOne()
    @JoinColumn(name = "parent_comment_id")
    Comment parent();

    @OneToMany(mappedBy = "parent")
    List<Comment> childComment();

    /**
     * 个人网站
     */
    @Null
    String website();

    /**
     * 若nickname为QQ号，就把 nickname 和avatar 设置为QQ昵称 和 QQ头像，并将此字段设置为QQ号的备份
     */
    @Null
    String qq();
}

