package none.nintha.vtracer.entity;

public class BiliMember {
    private long mid;
    private long fans;
    private String name;
    private String face;
    private long attention;
    private long friend;
    private int sex;
    private String sign;
    private long archive; // 投稿数量

    public long getArchive() {
        return archive;
    }

    public void setArchive(long archive) {
        this.archive = archive;
    }

    public long getMid() {
        return mid;
    }

    public void setMid(long mid) {
        this.mid = mid;
    }

    public long getFans() {
        return fans;
    }

    public void setFans(long fans) {
        this.fans = fans;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFace() {
        return face;
    }

    public void setFace(String face) {
        this.face = face;
    }

    public long getAttention() {
        return attention;
    }

    public void setAttention(long attention) {
        this.attention = attention;
    }

    public long getFriend() {
        return friend;
    }

    public void setFriend(long friend) {
        this.friend = friend;
    }

    public int getSex() {
        return sex;
    }

    public void setSex(int sex) {
        this.sex = sex;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    @Override
    public String toString() {
        return "BiliMember{" +
                "mid=" + mid +
                ", fans=" + fans +
                ", name='" + name + '\'' +
                ", face='" + face + '\'' +
                ", attention=" + attention +
                ", friend=" + friend +
                ", sex=" + sex +
                ", sign='" + sign + '\'' +
                ", archive=" + archive +
                '}';
    }
}
