package com.kaige.service.impl;

import com.kaige.constant.RedisKeyConstants;
import com.kaige.entity.*;
import com.kaige.entity.dto.*;
import com.kaige.handler.exception.NotFoundException;
import com.kaige.repository.BlogRepository;
import com.kaige.service.BlogService;
import com.kaige.service.RedisService;
import com.kaige.utils.JacksonUtils;
import com.kaige.utils.markdown.MarkdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
@Slf4j
public class BlogServiceImpl implements BlogService {

    @Autowired
    private RedisService redisService;

    @Autowired
    private BlogRepository blogRepository;

    private final JSqlClient jSqlClient;
    public BlogServiceImpl(JSqlClient jSqlClient) {
        this.jSqlClient = jSqlClient;
    }

    BlogTable blog = BlogTable.$;

    //博客简介列表排序方式
    private static final String orderBy = "is_top desc, create_time desc";
    private static final String PRIVATE_BLOG_DESCRIPTION = "这篇文章是私密的，只有密码才能查看";
    //每页显示5条博客简介
    private static final int pageSize = 5;

    //最新推荐博客显示3条
    private static final int newBlogPageSize = 3;

    @PostConstruct
    private void initBlogViewsToRedis(){
        String viewsKey = RedisKeyConstants.BLOG_VIEWS_MAP;
        if(!redisService.hasKey(viewsKey)){
            Map<BigInteger,Integer> blogViewsMap = getBlogViewMap();
            redisService.saveMapToHash(viewsKey,blogViewsMap);
            System.out.println("存入到redis成功"+blogViewsMap);
        }
    }

    private Map<BigInteger, Integer> getBlogViewMap() {
       List<BLogViewsView> bLogViewsViews = blogRepository.getBlogViewMap();
        HashMap<BigInteger, Integer> map = new HashMap<>(123);
        for (BLogViewsView bLogViewsView : bLogViewsViews) {
            BigInteger id = bLogViewsView.getId();
            Integer views = bLogViewsView.getViews();
            map.put(id,views);
        }
        return map;
    }


    @Override
    /**
     * 根据分类名称获取 公开 文章列表
     */
    public Page<Blog> getBlogListByCategoryName(String categoryName, Integer pageNum) {

        return jSqlClient.createQuery(blog)
                .where(blog.category().categoryName().eq(categoryName))
                .orderBy(Predicate.sql("%v",it->it.value(orderBy)))
                .select(blog.fetch(
                        BlogFetcher.$
                              .title()
                                .description()
                                .createTime()
                                .views()
                                .words()
                                .readTime()
                                .Top()
                                .password()
                                .Published()
                                .category(CategoryFetcher.$
                                       .categoryName())
                                .tags(TagFetcher.$
                                       .tagName()
                                        .color())
                ))
                .fetchPage(pageNum-1,10);
    }

    /**
     * 按照分页信息 查询博客简略信息
     * @param pageNum
     * @return
     */
    @Override
    public Page<BlogInfoView> getBlogListByIsPublished(Integer pageNum) {
//        从缓存查询
        String redisKey = RedisKeyConstants.HOME_BLOG_INFO_LIST;
        Page<BlogInfoView> pageResultFromRedis = redisService.getBlogInfoPageResultByPublish(redisKey,pageNum);
        if(pageResultFromRedis!=null){
            setBlogViewsFromRedisToPageResult(pageResultFromRedis);
            return pageResultFromRedis;
        }
        Page<BlogInfoView> blogPage = blogRepository.getBlogListByIsPublished(pageNum,pageSize,orderBy);
        List<BlogInfoView> blogInfoViewList = blogPage.getRows();
        processBlogInfoViewListPassword(blogInfoViewList);
        setBlogViewsFromRedisToPageResult(blogPage);
        redisService.saveKVToHash(redisKey,pageNum,blogPage);
        return blogPage;
    }

    private void setBlogViewsFromRedisToPageResult(Page<BlogInfoView> pageResultFromRedis) {
        String blogViewsKey = RedisKeyConstants.BLOG_VIEWS_MAP;
        List<BlogInfoView> blogInfoViewList = pageResultFromRedis.getRows();
        for (int i = 0; i < blogInfoViewList.size(); i++) {
            // 如果是从 从redis 中获取到 HOME_BLOG_INFO_LIST
            // 将json格式转换为 BlogInfoView 对象
            BlogInfoView blogInfoView = JacksonUtils.convertValue(blogInfoViewList.get(i), BlogInfoView.class);
            BigInteger id = blogInfoView.getId();
                int view = (int) redisService.getValueByHashKey(blogViewsKey, id);
                blogInfoView.setViews(view);
                blogInfoViewList.set(i,blogInfoView);
            }
    }

    private void processBlogInfoViewListPassword(List<BlogInfoView> blogInfoViewList) {
        for (BlogInfoView blogInfoView : blogInfoViewList) {
            String password = blogInfoView.getPassword();
            if(password!= null &&!password.equals("")){
                blogInfoView.setPassword("");
                blogInfoView.setPrivacy(true);
            }else {
                blogInfoView.setPrivacy(false);
                blogInfoView.setDescription(PRIVATE_BLOG_DESCRIPTION);
            }
        }
    }

    @Override
    public BlogDetailView getBlogByIdAndIsPublished(Long id) {
//        按id查找公布的文章
        BlogDetailView blog = blogRepository.getBlogByIdAndIsPublished(id);
        if(blog == null){
            throw new NotFoundException("文章不存在");
        }
//        每篇文章的浏览量需要单独存储，使用 Hash 类型可以将所有文章的浏览量存储在一个 hash 键下，
//        而不是每篇文章都使用一个独立的键。
       int view = (int) redisService.getValueByHashKey(RedisKeyConstants.BLOG_VIEWS_MAP,blog.getId());
        //      更新redis   文章浏览量+1
        blog.setContent(MarkdownUtils.markdownToHtmlExtensions(blog.getContent()));
        blog.setViews(view);
        return blog;
    }

    @Override
    public String getBlogPassword(BigInteger id) {
        Blog blog2 = jSqlClient.createQuery(blog)
                .where(blog.id().eq(id))
                .select(blog.fetch(
                        BlogFetcher.$
                                .password()
                )).fetchOne();
        return blog2.password();
    }

    @Override
    public List<Blog> getSearchBlogListByQueryAndPublished(String trim) {

        List<Blog> searchBlogListByQueryAndPublished = blogRepository.getSearchBlogListByQueryAndPublished(trim);
//       只显示 以目标字符串 为中心的 21个字符
        for (Blog blog : searchBlogListByQueryAndPublished) {
            String content = blog.content();
            int index = content.indexOf(trim);
            int start = Math.max(0, index - 10);
            int end = Math.min(content.length(), index + trim.length() + 10);
            String substring = content.substring(start, end);

            Blog blog1 = Immutables.createBlog(blog, it -> {
                it.setContent(substring);
            });
//          System.out.println(blog1.toString());
            searchBlogListByQueryAndPublished.set(searchBlogListByQueryAndPublished.indexOf(blog),blog1);
        }
        return searchBlogListByQueryAndPublished;
    }

    /**
     * 查询对应的博客是否开启 评论
     * @param blogId
     * @return
     */
    @Override
    public Boolean getCommentEnabledByBlogId(BigInteger blogId) {
        return blogRepository.getCommentEnabledByBlogId(blogId);
    }

    /**
     * 查询博客是否公开
     * @param blogId
     * @return
     */
    @Override
    public Boolean getPublishedByBlogId(BigInteger blogId) {
        return blogRepository.getPublisheByBlogId(blogId);
    }

    @Override
    public List<NewBlogView> getNewBlogListByIsPublished() {
        String newBlogListKey = RedisKeyConstants.NEW_BLOG_LIST;
         List<NewBlogView> newBlogViewsFromRedis = redisService.getListByValues(newBlogListKey);
         if (newBlogViewsFromRedis!=null){
             return newBlogViewsFromRedis;
         }
        //然后从数据库中查询
        List<NewBlogView> newBlogViews = blogRepository.getNewBlogListByIsPublished(newBlogPageSize);
        for (NewBlogView newBlogView : newBlogViews) {
            if (!"".equals(newBlogView.getPassword())){
                newBlogView.setPassword("");
                newBlogView.setPrivacy(true);
            }else {
                newBlogView.setPrivacy(false);
            }
        }
        redisService.saveListToValue(newBlogListKey,newBlogViews);
        return newBlogViews;
    }

    @Override
    public List<RandomBlogView> getRandomBlogList() {
        List<RandomBlogView> randomBlogList = blogRepository.getRandomBlogList();
        for (RandomBlogView randomBlogView : randomBlogList) {
            if (!"".equals(randomBlogView.getPassword())){
                randomBlogView.setPassword("");
                randomBlogView.setPrivacy(true);
            }else {
                randomBlogView.setPrivacy(false);
            }
        }
        return randomBlogList;
    }

    /**
     文章归档  按照年月  统计文章数量
      */
    @Override
    //TODO 完善字段显示，createTIme格式化--> 18日 留给前端完成吧
    public Map<String, Object> getArchiveBlogAndCountByIsPublished() {
//        查缓存
        String archiveBlogMapKey = RedisKeyConstants.ARCHIVE_BLOG_MAP;
        Map<String, Object> mapByValueFromRedis = redisService.getMapByValue(archiveBlogMapKey);
        if(mapByValueFromRedis!=null){
            return mapByValueFromRedis;
        }
//        按照文章是否公布，对年和月进行统计
        List<LocalDateTime> execute = jSqlClient.createQuery(blog)
                .where(blog.Published().eq(true))
                .select(blog.createTime())
                .execute();
//        查询 公开文章博客数量
        Long count = jSqlClient.createQuery(blog)
                .where(blog.Published().eq(true))
                .select(blog).fetchUnlimitedCount();

        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy年MM月");
        // 存储格式化后的结果
        List<String> formattedDates = new ArrayList<>();
        // 对每个日期进行格式化
        for (LocalDateTime date : execute) {
            // 格式化日期为 "yyyy年MM月"
            String formattedDate = date.format(outputFormatter);
            formattedDates.add(formattedDate);
        }
        Map<String, List<BlogArchiveView>> archiveMap = new LinkedHashMap<>();
        // 遍历格式化后的日期列表
        for (String s : formattedDates) {

            List<BlogArchiveView> execute1 = jSqlClient.createQuery(blog)
                    .where(Predicate.sql("DATE_FORMAT(create_time, '%Y年%m月') = %v", it -> it.value(s)))
                    .select(blog.fetch(
                            BlogArchiveView.class
                    ))
                    .execute();

            for (BlogArchiveView blogArchiveView : execute1) {
                if (!"".equals(blogArchiveView.getPassword())){
                    blogArchiveView.setPassword("");
                    blogArchiveView.setPrivacy(true);
                }else {
                    blogArchiveView.setPrivacy(false);
                }
            }

            archiveMap.put(s,execute1);
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("count",count);
        map.put("bolgMap",archiveMap);
        redisService.saveMapToValue(archiveBlogMapKey,map);
        return map;
    }
}
