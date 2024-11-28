package com.kaige.service.impl;

import com.kaige.constant.RedisKeyConstants;
import com.kaige.entity.*;
import com.kaige.entity.vo.FriendInfoVo;
import com.kaige.service.FriendService;
import com.kaige.service.RedisService;
import com.kaige.utils.markdown.MarkdownUtils;
import org.babyfish.jimmer.sql.JSqlClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FriendServiceImpl implements FriendService {

    @Autowired
    private RedisService redisService;
    private final JSqlClient sqlClient;

    public FriendServiceImpl(JSqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    @Override
    public List<Friend> getFriendList() {
         FriendTable friend = FriendTable.$;
        return sqlClient.createQuery(friend)
                .select(friend)
                .execute();
    }

    @Override
    public FriendInfoVo getSiteFriendinfo() {
        SiteSettingTable siteSettingTable = SiteSettingTable.$;

        String redisKey = RedisKeyConstants.FRIEND_INFO_MAP;
        FriendInfoVo friendInfoVo = redisService.getObjectByValue(redisKey,SiteSetting.class);
        if(friendInfoVo!= null){
            return friendInfoVo;
        }
        List<SiteSetting> settingListOfFriend = sqlClient.createQuery(siteSettingTable)
                .where(siteSettingTable.type().eq(4))
                .select(siteSettingTable.fetch(
                        SiteSettingFetcher.$
                                .nameEn()
                                .value()
                ))
                .execute();
        FriendInfoVo friendInfoVo1 = new FriendInfoVo();
        for (SiteSetting setting : settingListOfFriend) {
            if("friendContent".equals(setting.nameEn())){
                friendInfoVo1.setContent(MarkdownUtils.markdownToHtmlExtensions(setting.value()));
            }
            else if("friendCommentEnabled".equals(setting.nameEn())){
                friendInfoVo1.setCommentEnabled(true);
            }
        }
        redisService.saveObjectToValue(redisKey,friendInfoVo1);
        return friendInfoVo1;
    }
}
