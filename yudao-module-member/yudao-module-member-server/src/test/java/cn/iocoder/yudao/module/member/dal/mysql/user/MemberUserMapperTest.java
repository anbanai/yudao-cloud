package cn.iocoder.yudao.module.member.dal.mysql.user;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberUserMapperTest extends BaseDbUnitTest {

    @Resource
    private MemberUserMapper memberUserMapper;

    @Test
    void selectListByTagIds_returnsSortedUnionAndHonorsLimit() {
        MemberUserDO first = insertUser("13800000001", Arrays.asList(1L, 2L));
        MemberUserDO second = insertUser("13800000002", List.of(2L));
        insertUser("13800000003", List.of(3L));

        List<MemberUserDO> matched = memberUserMapper.selectListByTagIds(Arrays.asList(1L, 2L), 5001);
        assertEquals(Arrays.asList(first.getId(), second.getId()), matched.stream()
                .map(MemberUserDO::getId).collect(Collectors.toList()));
        assertEquals(1, memberUserMapper.selectListByTagIds(Arrays.asList(1L, 2L), 1).size());
    }

    private MemberUserDO insertUser(String mobile, List<Long> tagIds) {
        MemberUserDO user = new MemberUserDO().setNickname("测试会员").setStatus(0)
                .setMobile(mobile).setPassword("").setRegisterIp("127.0.0.1").setTagIds(tagIds);
        memberUserMapper.insert(user);
        return user;
    }
}
