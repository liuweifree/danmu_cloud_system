package cn.partytime.model.wechat;

import lombok.Data;

/**
 * Created by admin on 2018/4/25.
 */

@Data
public class UseSecretInfo {
    private String openId;

    private String session_key;

    private String unionId;

    private int expire;

    public UseSecretInfo(String openId, String session_key) {
        this.openId = openId;
        this.session_key = session_key;
    }


    public UseSecretInfo(String openId, String session_key,String unionId,int expire) {
        this.openId = openId;
        this.session_key = session_key;
        this.unionId = unionId;
        this.expire = expire;
    }


}
