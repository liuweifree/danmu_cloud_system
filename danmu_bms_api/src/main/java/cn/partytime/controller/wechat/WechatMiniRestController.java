package cn.partytime.controller.wechat;

import ch.qos.logback.core.util.FileUtil;
import cn.partytime.cache.wechatmin.WechatMiniCacheService;
import cn.partytime.common.constants.PartyConst;
import cn.partytime.common.util.DateUtils;
import cn.partytime.common.util.FileUtils;
import cn.partytime.common.util.IntegerUtils;
import cn.partytime.common.util.ListUtils;
import cn.partytime.dataRpc.RpcCmdService;
import cn.partytime.dataRpc.RpcPartyService;
import cn.partytime.model.*;
import cn.partytime.model.manager.*;
import cn.partytime.model.wechat.UseSecretInfo;
import cn.partytime.model.wechat.WeChatMiniUser;
import cn.partytime.model.wechat.WechatUser;
import cn.partytime.model.wechat.WechatUserInfo;
import cn.partytime.service.*;
import cn.partytime.service.daynamic.DynamicContentService;
import cn.partytime.service.wechat.BmsWechatMiniService;
import cn.partytime.service.wechat.WeChatMiniUserService;
import cn.partytime.service.wechat.WechatUserInfoService;
import cn.partytime.service.wechat.WechatUserService;
import cn.partytime.util.FileUploadUtil;
import cn.partytime.util.WeixinUtil;
import cn.partytime.wechat.payService.WechatPayService;
import cn.partytime.wechat.pojo.UserInfo;
import cn.partytime.wechat.pojo.WxJsConfig;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;

/**
 * Created by admin on 2018/4/25.
 */


@Slf4j
@RestController
@RequestMapping(value = "/v1/api/wechatMini")
public class WechatMiniRestController {

    @Autowired
    private BmsDanmuService bmsDanmuService;

    @Autowired
    private ResourceFileService resourceFileService;

    @Autowired
    private BmsWechatUserService bmsWechatUserService;

    @Autowired
    private BmsColorService bmsColorService;

    @Autowired
    private FastDanmuService fastDanmuService;

    @Autowired
    private FileUploadUtil fileUploadUtil;

    @Autowired
    private RpcCmdService rpcCmdService;

    @Autowired
    private WechatPayService wechatPayService;

    @Autowired
    private BmsReportService bmsReportService;

    @Autowired
    private RpcPartyService rpcPartyService;

    @Autowired
    private WechatUserService wechatUserService;

    @Autowired
    private WechatUserInfoService wechatUserInfoService;

    @Autowired
    private WeChatMiniUserService weChatMiniUserService;

    @Autowired
    private WechatMiniCacheService wechatMiniCacheService;

    @Autowired
    private BmsPartyService bmsPartyService;

    @Autowired
    private DanmuAddressService danmuAddressService;


    @Autowired
    private PartyService partyService;

    @Autowired
    private PartyAddressRelationService partyAddressRelationService;


    @Autowired
    private BmsWechatMiniService bmsWechatMiniService;

    @Autowired
    private DynamicContentService dynamicContentService;

    @Value("${wechat.dynamicvoicePath}")
    private String voicePath;

    @Value("${wechat.dynamicvoiceTempPath}")
    private String tempPath;

    @RequestMapping("/fileUpload")
    public RestResultModel fileUpload(@RequestParam("file") MultipartFile file) throws IOException {

        RestResultModel restResultModel = new RestResultModel();
        if(file.isEmpty()){
            restResultModel.setData("500");
            return restResultModel;
        }
        String fileName = file.getOriginalFilename();

        log.info("文件名称:{}",fileName);
        InputStream inputStream = file.getInputStream();

        String sourceName = fileName.substring(0,fileName.lastIndexOf("."));

        OutputStream os = null;
        try {
            // 2、保存到临时文件
            // 1K的数据缓冲
            byte[] bs = new byte[1024];
            // 读取到的数据长度
            int len;
            // 输出的文件流保存到本地文件

            File tempFile = new File(tempPath);
            if (!tempFile.exists()) {
                tempFile.mkdirs();
            }

            String aimPath = tempFile.getPath() + File.separator + fileName;
            os = new FileOutputStream(aimPath);
            // 开始读取
            while ((len = inputStream.read(bs)) != -1) {
                os.write(bs, 0, len);
            }
            os.flush();
            os.close();
            inputStream.close();
            String command = "/usr/local/install/silk-v3-decoder/converter.sh "+aimPath +" mp3";
            log.info("command:{}",command);

            bmsWechatMiniService.execShell(command);

            String sourceMp3 = tempFile.getPath()+File.separator+sourceName+".mp3";
            String aimPcm = tempFile.getPath()+File.separator+ sourceName+".pcm";


            log.info("sourceMp3:{}",sourceMp3);
            log.info("aimPcm:{}",aimPcm);
            command = "ffmpeg -y  -i  "+ sourceMp3 +" -acodec pcm_s16le -f s16le -ac 1 -ar 16000 "+aimPcm;



            log.info("command:{}",command);
            bmsWechatMiniService.execShell(command);
            String result = bmsWechatMiniService.convertVedioToWord(aimPcm);
            log.info("result============={}",result);
            restResultModel.setResult(200);
            restResultModel.setData(result);

            log.info("restResultModel:{}",JSON.toJSONString(restResultModel));
        } catch (IOException e) {
            e.printStackTrace();
            log.info("==================================exception1");
        } catch (Exception e) {
            e.printStackTrace();
            log.info("==================================exception2");
        } finally {
            log.info("==================================finally");
        }
        return restResultModel;
    }

    @RequestMapping(value = "/findAddressList", method = RequestMethod.GET)
    public RestResultModel findAddressList(HttpServletRequest request) {
        RestResultModel restResultModel = new RestResultModel();
        List<DanmuAddress> danmuAddressList =  danmuAddressService.findByType(0);
        log.info("danmuAddressList:{}",JSON.toJSONString(danmuAddressList));

        Set<String> addressSet = new HashSet<String>();
        danmuAddressList.forEach(danmuAddress -> addressSet.add(danmuAddress.getId()));
        List<Party> partyList =  partyService.findByTypeAndStatusLess(PartyConst.PARTY_TYPE_PARTY,3);
        if(ListUtils.checkListIsNotNull(partyList)){
            List<String> partyIdList = new ArrayList<String>();
            partyList.forEach(party -> partyIdList.add(party.getId()));

            List<PartyAddressRelation> partyAddressRelationList = partyAddressRelationService.findByPartyIds(partyIdList);
            List<String> addressList = new ArrayList<String>();
            if(ListUtils.checkListIsNotNull(partyAddressRelationList)){
                partyAddressRelationList.forEach(partyAddressRelation -> addressList.add(partyAddressRelation.getAddressId()));
            }
            List<DanmuAddress> danmuAddressTempList = danmuAddressService.findDanmuAddressByIdList(addressList);
            List<DanmuAddress> partyAddressList = new ArrayList<DanmuAddress>();
            for(DanmuAddress danmuAddress:danmuAddressTempList){
                if(!addressSet.contains(danmuAddress.getId())){
                    partyAddressList.add(danmuAddress);
                }
            }
            danmuAddressList.addAll(partyAddressList);
        }
        restResultModel.setResult(200);
        if(ListUtils.checkListIsNull(danmuAddressList)){
            danmuAddressList = new ArrayList<DanmuAddress>();
        }
        restResultModel.setData(danmuAddressList);
        return restResultModel;
    }
    @RequestMapping(value = "/wxBingPay", method = RequestMethod.POST)
    public RestResultModel wxBingPay(HttpServletRequest request) {
        RestResultModel restResultModel = new RestResultModel();
        StringBuffer url = request.getRequestURL();
        //log.info("url:{}",url.);
        //url.replace("test","www.party-time.cn");
        //String openId = request.getParameter("openId");
        String userCookieKey = request.getParameter("userCookieKey");
        Object object =  wechatMiniCacheService.getWechatMiniUserCache(userCookieKey);
        String unionId = String.valueOf(object);
        WechatUser wechatUser = wechatUserService.findByUnionId(unionId);
        String openId = wechatUser.getOpenId();
        openId= "ol5eSwm5tmmaKj9UjqZmYdUkZmZM";
        String trueUrl = url.toString().replace("test","www.party-time.cn") + "?&openId="+openId;
        WxJsConfig wxJsConfig = wechatPayService.createWxjsConfig(trueUrl);
        log.info("wxJsConfig:{}",JSON.toJSONString(wxJsConfig));

        String nonceStr = wxJsConfig.getNonceStr();
        String timestamp = wxJsConfig.getTimestamp();
        String body = "弹幕电影-打赏1分";
        String detail="";
        String attach = "";
        Integer total_fee = 1;
        String clientIp = request.getHeader("x-forwarded-for");
        if(StringUtils.isEmpty(clientIp)){
            clientIp = request.getRemoteAddr();
        }
        if(!StringUtils.isEmpty(clientIp) && clientIp.indexOf(",")!=-1){
            clientIp = clientIp.substring(0,clientIp.indexOf(","));
        }
        Map<String,String> map = new HashMap<>();
        map = wechatPayService.createUnifiedorder(nonceStr,timestamp,openId,body,detail,attach,total_fee,clientIp);
        log.info("map:{}",JSON.toJSONString(map));

        restResultModel.setResult(200);
        restResultModel.setData(map);
        return restResultModel;
    }


    @RequestMapping(value = "/findAdvanceTmplate", method = RequestMethod.POST)
    public RestResultModel findAdvanceTmplate(HttpServletRequest request) {
        String key = request.getParameter("key");
        CmdTempAllData cmdTempAllData =  rpcCmdService.findCmdTempAllDataByKeyFromCache(key);
        RestResultModel restResultModel = new RestResultModel();
        restResultModel.setResult(200);
        restResultModel.setData(cmdTempAllData);
        return restResultModel;
    }


    @RequestMapping(value = "/findPartyInfoByLocation", method = RequestMethod.POST)
    public RestResultModel findPartyInfoByLocation(HttpServletRequest request) {

        String latitude = request.getParameter("latitude");
        String longitude = request.getParameter("longitude");

        log.info("latitude:{},longitude:{}",latitude,longitude);
        RestResultModel restResultModel = new RestResultModel();
        PartyLogicModel party = rpcPartyService.findPartyByLonLat(Double.parseDouble(longitude+""),Double.parseDouble(latitude+""));
        log.info("PartyLogicModel:{}",JSON.toJSONString(party));
        if( null == party){
            restResultModel.setResult(404);
            restResultModel.setResult_msg("没有活动");
            return restResultModel;
        }

        Map<String, Object> resourceFileModels = resourceFileService.findResourceMapByPartyId(party.getPartyId());

        List<ResourceFile> all = new ArrayList<>();
        List<ResourceFile> expressionconstant = (List<ResourceFile>)resourceFileModels.get("expressionconstant");
        List<ResourceFile> expressions = (List<ResourceFile>)resourceFileModels.get("expressions");

        if( null != expressionconstant){
            all.addAll(expressionconstant);
        }

        if( null != expressions){
            all.addAll(expressions);
        }
        Map<String,Object> map = new HashMap<>();
        map.put("expressions",all );

        if (null != resourceFileModels.get("h5Background")) {
            List reList = (ArrayList) resourceFileModels.get("h5Background");
            if (reList.size() > 0) {
                map.put("background", reList.get(0));
            }
        }
        map.put("colors", bmsColorService.findDanmuColor(0));
        //map.put("openId", openId);
        map.put("partyId",party.getPartyId());
        map.put("addressId",party.getAddressId());

        List<FastDanmu> fastDanmuList = fastDanmuService.findByPartyId(party.getPartyId());
        if( null != fastDanmuList && fastDanmuList.size() > 0){
            map.put("fastdmList",fastDanmuList);
        }

        String fileUploadUrl = fileUploadUtil.getUrl();
        map.put("baseUrl",fileUploadUrl);
        map.put("partyName",party.getPartyName());
        //map.put("openId",openId);

        //log.info("partyInfo:{}",JSON.toJSONString(map));
        restResultModel.setResult(200);
        restResultModel.setData(map);
        return restResultModel;
    }



    @RequestMapping(value = "/findPartyInfo", method = RequestMethod.POST)
    public RestResultModel partyInfo(HttpServletRequest request) {
        /*String code  = request.getParameter("code");
        log.info("小程序请求的code:{}",code);


        UseSecretInfo useSecretInfo = WeixinUtil.getUserOpenIdAndSessionKey(code);
        if(useSecretInfo==null){
            //TODO:获取不到用户信息
        }

        RestResultModel restResultModel = new RestResultModel();
        String openId = useSecretInfo.getOpenId();

        //从数据库中获取用户微信信息
        WechatUser wechatUser = bmsWechatUserService.findByOpenId(openId);
        log.info("wechatUser:", JSON.toJSONString(wechatUser));

        //从微信服务器获取用户信息
        UserInfo userInfo = WeixinUtil.getUserInfo(bmsWechatUserService.getAccessToken().getToken(), openId);
        String unionId = userInfo.getUnionid();

        if( null != userInfo){
            wechatUserService.updateUserInfo(userInfo.toWechatUser());
        }*/
        //String latitude = request.getParameter("latitude");
        //String longitude = request.getParameter("longitude");
        String userCookieKey = request.getParameter("userCookieKey");
        Object object =  wechatMiniCacheService.getWechatMiniUserCache(userCookieKey);
        String unionId = String.valueOf(object);
        WechatUser wechatUser = wechatUserService.findByUnionId(unionId);
        String wechatId =wechatUser.getId();
        WechatUserInfo wechatUserInfo = wechatUserInfoService.findByWechatId(wechatId);

        double latitude = wechatUserInfo.getLastLatitude();
        double longitude = wechatUserInfo.getLastLongitude();

        RestResultModel restResultModel = new RestResultModel();
        PartyLogicModel party = rpcPartyService.findPartyByLonLat(longitude,latitude);
        log.info("PartyLogicModel:{}",JSON.toJSONString(party));
        if( null == party){
            restResultModel.setResult(404);
            restResultModel.setResult_msg("位置授权");
            return restResultModel;
        }

        Map<String, Object> resourceFileModels = resourceFileService.findResourceMapByPartyId(party.getPartyId());

        List<ResourceFile> all = new ArrayList<>();
        List<ResourceFile> expressionconstant = (List<ResourceFile>)resourceFileModels.get("expressionconstant");
        List<ResourceFile> expressions = (List<ResourceFile>)resourceFileModels.get("expressions");

        if( null != expressionconstant){
            all.addAll(expressionconstant);
        }

        if( null != expressions){
            all.addAll(expressions);
        }
        Map<String,Object> map = new HashMap<>();
        map.put("expressions",all );

        if (null != resourceFileModels.get("h5Background")) {
            List reList = (ArrayList) resourceFileModels.get("h5Background");
            if (reList.size() > 0) {
                map.put("background", reList.get(0));
            }
        }
        map.put("colors", bmsColorService.findDanmuColor(0));
        //map.put("openId", openId);
        map.put("partyId",party.getPartyId());
        map.put("addressId",party.getAddressId());

        List<FastDanmu> fastDanmuList = fastDanmuService.findByPartyId(party.getPartyId());
        if( null != fastDanmuList && fastDanmuList.size() > 0){
            map.put("fastdmList",fastDanmuList);
        }

        String fileUploadUrl = fileUploadUtil.getUrl();
        map.put("baseUrl",fileUploadUrl);
        map.put("partyName",party.getPartyName());
        //map.put("openId",openId);

        //log.info("partyInfo:{}",JSON.toJSONString(map));
        restResultModel.setResult(200);
        restResultModel.setData(map);
        return restResultModel;
    }





    @RequestMapping(value = "/historyDanmu", method = RequestMethod.GET)
    public PageResultModel historyDanmu(HttpServletRequest request) {


        String userCookieKey = request.getParameter("userCookieKey");

        String pageNo = request.getParameter("pageNo");

        String pageSize = request.getParameter("pageSize");
        String latitude = request.getParameter("latitude");
        String longitude = request.getParameter("longitude");

        //Object object =  wechatMiniCacheService.getWechatMiniUserCache(userCookieKey);
        //String unionId = String.valueOf(object);
        //PartyLogicModel party = bmsPartyService.findCurrentPartyByUnionId(unionId);
        PartyLogicModel party = rpcPartyService.findPartyByLonLat(Double.parseDouble(longitude+""),Double.parseDouble(latitude+""));
        log.info("party:{}",JSON.toJSONString(party));
        PageResultModel pageResultModel = bmsDanmuService.findPageResultDanmuModel(IntegerUtils.objectConvertToInt(pageNo)-1,IntegerUtils.objectConvertToInt(pageSize),party.getAddressId(),party.getPartyId(),1);
        return pageResultModel;
    }

    @RequestMapping(value = "/historyDanmu/report", method = RequestMethod.GET)
    public RestResultModel report(HttpServletRequest request){
        RestResultModel restResultModel = new RestResultModel();
        String userCookieKey = request.getParameter("userCookieKey");

        Object object =  wechatMiniCacheService.getWechatMiniUserCache(userCookieKey);
        String unionId = String.valueOf(object);
        String danmuId = request.getParameter("danmuId");
        String result = bmsReportService.reportDanmuByUnionId(unionId,danmuId);
        if(StringUtils.isEmpty(result)){
            restResultModel.setResult(200);
        }else{
            restResultModel.setResult(500);
            restResultModel.setResult_msg(result);
        }
        return restResultModel;
    }
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public RestResultModel login(HttpServletRequest request) {
        RestResultModel restResultModel = new RestResultModel();
        String code  = request.getParameter("code");
        log.info("小程序登陆请求的code:{}",code);
        UseSecretInfo useSecretInfo = WeixinUtil.getMiniProgramUserOpenIdAndSessionKey(code);
        String openId = useSecretInfo.getOpenId();
        String unionId = useSecretInfo.getUnionId();

        WeChatMiniUser weChatMiniUser =  weChatMiniUserService.findByUnionId(unionId);

        log.info("weChatMiniUser:{}",JSON.toJSONString(weChatMiniUser));

        if(weChatMiniUser==null){
            weChatMiniUser = new WeChatMiniUser();
            weChatMiniUser.setUnionId(unionId);
            weChatMiniUser.setOpenId(openId);
            weChatMiniUserService.saveWeChatMiniUser(weChatMiniUser);
        }
        log.info("登陆时候获取的openId:{}",openId);
        WechatUser wechatUser = wechatUserService.findByUnionId(unionId);
        if(wechatUser==null){
            wechatUser = new WechatUser();
        }
        //wechatUser.setOpenId(openId);
        wechatUser =  wechatUserService.save(wechatUser);
        log.info("wechatUser:{}",JSON.toJSONString(wechatUser));

        String wechatId =wechatUser.getId();
        WechatUserInfo wechatUserInfo = wechatUserInfoService.findByWechatId(wechatId);
        if(wechatUserInfo==null) {
            wechatUserInfo = new WechatUserInfo();
        }
        wechatUserInfoService.update(wechatUserInfo);


        String cookie = BCrypt.hashpw(unionId, BCrypt.gensalt());
        wechatMiniCacheService.setWechatMiniUserCache(cookie,unionId);

        restResultModel.setResult(200);
        Map<String,String> resultMap = new HashMap<String,String>();
        resultMap.put("userCookieKey",cookie);
        restResultModel.setData(resultMap);
        return  restResultModel;
    }
    @RequestMapping(value = "/updateWechatUser", method = RequestMethod.POST)
    public RestResultModel updateWechatUser(HttpServletRequest request){
        String userCookieKey = request.getParameter("userCookieKey");

        log.info("更新用户信息获取用户的userCookieKey:{}",userCookieKey);
        RestResultModel restResultModel = new RestResultModel();
        Object object =  wechatMiniCacheService.getWechatMiniUserCache(userCookieKey);
        String unionId = String.valueOf(object);
        WechatUser wechatUser =  wechatUserService.findByUnionId(unionId);

        log.info("取得的用户信息:{}",JSON.toJSONString(wechatUser));

        String avatarUrl = request.getParameter("avatarUrl");
        String city = request.getParameter("city");
        String country = request.getParameter("country");
        String gender = request.getParameter("gender");
        String nickName = request.getParameter("nickName");
        String language = request.getParameter("language");
        String province = request.getParameter("province");

        String latitude = request.getParameter("latitude");
        String longitude = request.getParameter("longitude");

        if(wechatUser ==null){
            wechatUser = new WechatUser();
        }
        if(!StringUtils.isEmpty(avatarUrl)){
            wechatUser.setImgUrl(avatarUrl);
        }
        if(!StringUtils.isEmpty(city)) {
            wechatUser.setCity(city);
        }
        if(!StringUtils.isEmpty(country)) {
            wechatUser.setCountry(country);
        }
        if(!StringUtils.isEmpty(gender)) {
            wechatUser.setSex(IntegerUtils.objectConvertToInt(gender));
        }
        if(!StringUtils.isEmpty(nickName)) {
            wechatUser.setNick(nickName);
        }
        if(!StringUtils.isEmpty(language)) {
            wechatUser.setLanguage(language);
        }
        if(!StringUtils.isEmpty(province)) {
            wechatUser.setProvince(province);
        }

        if(!StringUtils.isEmpty(latitude) && !StringUtils.isEmpty(longitude)) {
            wechatUser.setLatitude(Double.parseDouble(latitude+""));
            wechatUser.setLongitude(Double.parseDouble(longitude+""));
        }


        wechatUser = wechatUserService.save(wechatUser);
        String wechatId = wechatUser.getId();

        WechatUserInfo wechatUserInfo =  wechatUserInfoService.findByWechatId(wechatId);
        if(!StringUtils.isEmpty(latitude) && !StringUtils.isEmpty(longitude)) {
            wechatUserInfo.setLastLatitude(Double.parseDouble(latitude+""));
            wechatUserInfo.setLastLongitude(Double.parseDouble(longitude+""));
            //wechatUserInfoService.saveOrUpdate(wechatId,Double.parseDouble(longitude+""),Double.parseDouble(latitude+""));


            if( null != wechatUser.getAssignAddressTime()){
                Date now  = new Date();
                long aa = now.getTime() - wechatUser.getAssignAddressTime().getTime();
                if( (aa /(1000*60)) > 30 ){
                    wechatUserInfoService.update(wechatUserInfo);
                }
            }else{
                wechatUserInfoService.update(wechatUserInfo);
            }
        }
        restResultModel.setResult(200);
        restResultModel.setData(wechatUser);
        return  restResultModel;
    }

    @RequestMapping(value = "/wechartSend", method = RequestMethod.POST)
    public RestResultModel wechartSend(HttpServletRequest request) {
        log.info("小程序端，弹幕发送");
        String userCookieKey = request.getParameter("userCookieKey");
        Object object =  wechatMiniCacheService.getWechatMiniUserCache(userCookieKey);
        String unionId = String.valueOf(object);
        RestResultModel restResultModel = new RestResultModel();
        if (bmsDanmuService.checkFrequency(request)) {
            restResultModel.setResult(403);
            restResultModel.setResult_msg("Limited Frequency");
            log.info("小程序{}，发送弹幕,太频繁",unionId);
            return restResultModel;
        }else if(bmsDanmuService.checkDanmuIsRepeat(unionId,request.getParameter("message"))){
            restResultModel.setResult(403);
            restResultModel.setResult_msg("相同弹幕发送太多");
            log.info("小程序{}，相同弹幕发送太多",unionId);
            return restResultModel;
        }else {
            log.info("===========================================================");
            return bmsDanmuService.sendDanmuFromWechatMini(request,unionId,0);
        }

    }

    @RequestMapping(value = "/sendExpression", method = RequestMethod.POST)
    public RestResultModel sendExpression(HttpServletRequest request) {
        log.info("小程序端，发送表情");
        RestResultModel restResultModel = new RestResultModel();
        String userCookieKey = request.getParameter("userCookieKey");
        Object object =  wechatMiniCacheService.getWechatMiniUserCache(userCookieKey);
        String unionId = String.valueOf(object);
        if (bmsDanmuService.checkFrequency(request)) {
            restResultModel.setResult(403);
            restResultModel.setResult_msg("Limited Frequency");
            log.info("用户{}，发送弹幕,太频繁",unionId);
            return restResultModel;
        }else {
            return  bmsDanmuService.sendDanmuFromWechatMini(request,unionId,0);
        }
    }

}
