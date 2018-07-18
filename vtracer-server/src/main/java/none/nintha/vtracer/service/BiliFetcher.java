package none.nintha.vtracer.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import none.nintha.vtracer.entity.BiliMember;
import none.nintha.vtracer.entity.constant.MemberSex;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;

@Service
public class BiliFetcher {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final static String API_UP_TOTAL_VIEW = "http://api.bilibili.com/x/space/upstat?mid=${mid}";
    private final static String API_UP_SUBMIT = "http://space.bilibili.com/ajax/member/getSubmitVideos?mid=${mid}&pagesize=1&page=1&order=pubdate";
    private final static String API_VIDEO_STAT = "http://api.bilibili.com/x/web-interface/archive/stat?aid=${aid}";
    private final static String API_MEMBER_INFO = "http://api.bilibili.com/x/web-interface/card?mid=${mid}";
    private final static String API_VIDEO_HTML = "http://www.bilibili.com/video/av${aid}";

    public BiliMember fetchBiliMember(long mid) {
        try {
            BiliMember member = new BiliMember();
            member.setMid(mid);
            JsonNode node = mapper.readTree(new URL(API_MEMBER_INFO.replace("${mid}", mid + "")));
            JsonNode data = node.get("data");
            JsonNode card = data.get("card");

            member.setArchive(data.get("archive_count").asLong());
            member.setAttention(card.get("attention").asLong(0L));
            member.setFace(card.get("face").asText(Strings.EMPTY));
            member.setFans(card.get("fans").asLong(0L));
            member.setFriend(card.get("friend").asLong(0L));
            member.setName(card.get("name").asText(Strings.EMPTY));
            member.setSex(MemberSex.ofDesc(card.get("sex").asText()).map(e -> e.code).orElse(MemberSex.UNKNOWN.code));
            member.setSign(card.get("sign").asText());
            return member;
        } catch (IOException e) {
            logger.error("解析用户信息异常", e);
        }
        return null;
    }

    public long fetchMemberArchiveView(long mid){
        try {
            JsonNode node = mapper.readTree(new URL(API_UP_TOTAL_VIEW.replace("${mid}", mid + "")));
            return node.get("data").get("archive").get("view").asLong();
        } catch (IOException e) {
            logger.error("解析用户总播放量异常", e);
        }
        return 0;
    }

    public static void main(String[] args) {
        BiliMember biliMember = new BiliFetcher().fetchBiliMember(1492);
        System.out.println(biliMember);
    }
}
