package none.nintha.vtraceapi.entity;

import none.nintha.vtraceapi.util.CommonUtil;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * 混合查询类
 */
public class QueryReq {
    private static final Logger logger = LoggerFactory.getLogger(QueryReq.class);
    int pageNum = 1;
    int pageSize = 20;

    String title;
    String name;
    Integer keep;
    Integer mini;
    Long mid; // for video filter

    public QueryReq(int pageNum, int pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static QueryReq unpage() {
        return new QueryReq(1, 0);
    }

    public QueryReq() {
    }

    public int getSkip() {
        return (pageNum - 1) * pageSize;
    }

    public int getLimit() {
        return pageSize;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getKeep() {
        return keep;
    }

    public void setKeep(Integer keep) {
        this.keep = keep;
    }

    public Integer getMini() {
        return mini;
    }

    public void setMini(Integer mini) {
        this.mini = mini;
    }

    public Long getMid() {
        return mid;
    }

    public void setMid(Long mid) {
        this.mid = mid;
    }

    /**
     * 构建mongo 查询条件
     * @return
     */
    public Query toQuery() {
        Criteria criteria = new Criteria();
        if (Strings.isNotBlank(title)) {
            criteria.orOperator(
                    Criteria.where("aid").is(CommonUtil.parseLong(title, 0L)),
                    Criteria.where("title").regex(title, "i")
            );
        }
        if (Strings.isNotBlank(name)) {
            criteria.orOperator(
                    Criteria.where("mid").is(CommonUtil.parseLong(name, 0L)),
                    Criteria.where("name").regex(name, "i")
            );
        }
        if (keep != null) {
            criteria.and("keep").is(keep);
        }
        if (mini != null){
            criteria.and("mini").is(mini);
        }
        if (mid != null){
            criteria.and("mid").is(mid);
        }
        if (this.getLimit() == 0) {
            return new Query(criteria);
        }

        Query rs = new Query(criteria).limit(this.getLimit()).skip(this.getSkip());

        logger.debug("ToQuery#{}",rs);
        return rs;
    }
}
