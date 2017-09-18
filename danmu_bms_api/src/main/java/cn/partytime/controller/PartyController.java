package cn.partytime.controller;

import cn.partytime.controller.base.BaseAdminController;
import cn.partytime.dataRpc.RpcPartyService;
import cn.partytime.dataRpc.RpcPreDanmuService;
import cn.partytime.model.*;
import cn.partytime.model.manager.MovieAlias;
import cn.partytime.model.manager.Party;
import cn.partytime.service.*;
import com.thoughtworks.xstream.mapper.Mapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by liuwei on 2016/8/19.
 */

@RestController
@RequestMapping(value = "/v1/api/admin/party")
@Slf4j
public class PartyController extends BaseAdminController {

    @Autowired
    private PartyService partyService;

    @Autowired
    private RpcPartyService rpcPartyService;

    @Autowired
    private BmsPartyService bmsPartyService;

    @Autowired
    private BmsTimerDanmuService bmsTimerDanmuService;

    @Autowired
    private PartyAddressRelationService partyAddressRelationService;

    @Autowired
    private DanmuLibraryPartyService danmuLibraryPartyService;

    @Autowired
    private RpcPreDanmuService rpcPreDanmuService;

    /**
     *
     * @param pageNumber
     * @param pageSize
     * @param type 0:全部  1：活动  2：电影
     * @param status
     * @return
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public PageResultModel partyList(Integer pageNumber, Integer pageSize, Integer type, Integer status) {

        pageNumber = pageNumber -1;
        if( null == type ){
            type = -1;
        }
        if( null == status){
            status = 1;
        }
        return bmsPartyService.findAllByPage(pageNumber, pageSize,type,status);
    }

    @RequestMapping(value = "/partyReOpen", method = RequestMethod.GET)
    public RestResultModel partyList(String id) {
        log.info("id:{}",id);
        RestResultModel restResultModel = new RestResultModel();
        Party party =  partyService.findById(id);
        if(party!=null){
            party.setStartTime(null);
            party.setActivityStartTime(null);
            party.setEndTime(null);
            party.setStatus(0);
            partyService.updateParty(party);
            restResultModel.setResult(200);
            restResultModel.setResult_msg("OK");
        }else{
            restResultModel.setResult(500);
            restResultModel.setResult_msg("活动不存在");
        }
        return  restResultModel;
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    public RestResultModel saveParty(String id,String name, Integer type, String movieAlias,@RequestParam(value = "ids",required = false)String[] ids,@RequestParam(value = "densitrys",required = false)Integer[] densitrys,
                                     String shortName,String danmuLibraryId,String addressIds,Integer dmDensity) {
        RestResultModel restResultModel = new RestResultModel();

        if( null == type ){
            restResultModel.setResult(501);
            restResultModel.setResult_msg("没有正确的活动类型");
            return restResultModel;
        }

        Date startTime = null;
        Date endTime = null;
        //判断活动参数
        if( 0 == type){
            /**
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Date nowDate = DateUtils.getCurrentDate();
            try {
                startTime = dateFormat.parse(startTimeStr);
                endTime = dateFormat.parse(endTimeStr);
            } catch (ParseException e) {
                restResultModel.setResult(501);
                restResultModel.setResult_msg("开始和结束时间格式错误");
                log.error("", e);
                return restResultModel;
            }
            if (nowDate.after(startTime) || nowDate.after(endTime)) {
                restResultModel.setResult(501);
                restResultModel.setResult_msg("当前时间不能小于活动的开始时间和结束时间!");
                return restResultModel;
            }
            if (startTime.after(endTime) || startTimeStr.equals(endTimeStr)) {
                restResultModel.setResult(501);
                restResultModel.setResult_msg("开始时间要小于结束时间");
                return restResultModel;
            }**/

            if(StringUtils.isEmpty(addressIds)){
                restResultModel.setResult(501);
                restResultModel.setResult_msg("请选择场地");
                return restResultModel;
            }
        }else{
            //判断电影参数
            if( StringUtils.isEmpty(id)){
                List<Party> partyList = partyService.findByMovieAlias(movieAlias);
                if( null != partyList && partyList.size() > 0){
                    restResultModel.setResult(501);
                    restResultModel.setResult_msg("电影TMS指令不能重复,请下线其他的电影");
                    return restResultModel;
                }
            }else{
                Party party = partyService.findById(id);
                if( null!= party && !StringUtils.isEmpty(party.getMovieAlias()) && !StringUtils.isEmpty(movieAlias)){
                    if(!movieAlias.equals(party.getMovieAlias()) ){
                        List<Party> partyList = partyService.findByMovieAlias(movieAlias);
                        if( null != partyList && partyList.size() > 0){
                            restResultModel.setResult(501);
                            restResultModel.setResult_msg("电影TMS指令不能重复,请下线其他的电影");
                            return restResultModel;
                        }
                    }

                }
            }

        }

        Party party = null;
        try {
            if( StringUtils.isEmpty(id)){
                party = partyService.save(name, type, movieAlias ,startTime, endTime, shortName,danmuLibraryId,dmDensity);

            }else{
                party = partyService.update(id,name,type,movieAlias,danmuLibraryId,dmDensity);
            }

            //
            rpcPreDanmuService.setPreDanmuLibrarySortRule(party.getId());

            if(!StringUtils.isEmpty(addressIds)){
                if(addressIds.indexOf(",")!=-1){
                    String[] addressIdStrs = addressIds.split(",");
                    for( String addressId : addressIdStrs){
                        partyAddressRelationService.save(party.getId(),addressId);
                    }
                }else{
                    partyAddressRelationService.save(party.getId(),addressIds);
                }
            }
            if(null != ids && ids.length > 0){
                danmuLibraryPartyService.deleteByPartyId(party.getId());
                for(int i=0;i<ids.length;i++){
                    danmuLibraryPartyService.save(ids[i],party.getId(),densitrys[i]);
                }
            }
        } catch (IllegalArgumentException e) {
            restResultModel.setResult(501);
            restResultModel.setResult_msg(e.getMessage());
            return restResultModel;
        }
        restResultModel.setResult(200);
        restResultModel.setResult_msg("success");
        restResultModel.setData(party);
        return restResultModel;
    }

    @RequestMapping(value = "/del", method = RequestMethod.GET)
    public RestResultModel delParty(String id) {
        RestResultModel restResultModel = new RestResultModel();
        //partyService.deleteById(id);
        rpcPartyService.deleteParty(id);
        restResultModel.setResult(200);
        restResultModel.setResult_msg("success");
        return restResultModel;
    }



    @RequestMapping(value = "/name", method = RequestMethod.GET)
    public RestResultModel findPartyByName(String name) {
        RestResultModel restResultModel = new RestResultModel();
        Party party = partyService.findByName(name);
        restResultModel.setResult(200);
        restResultModel.setResult_msg("success");
        restResultModel.setData(party);
        return restResultModel;
    }

    @RequestMapping(value = "/shortName", method = RequestMethod.GET)
    public RestResultModel findPartyByShortName(String shortName) {
        RestResultModel restResultModel = new RestResultModel();
        Party party = partyService.findByShortName(shortName);
        restResultModel.setResult(200);
        restResultModel.setResult_msg("success");
        restResultModel.setData(party);
        return restResultModel;
    }

    @RequestMapping(value = "/checkPartyIsOver", method = RequestMethod.GET)
    public RestResultModel checkPartyIsOver(String id) {
        RestResultModel restResultModel = new RestResultModel();
        Party party = partyService.findById(id);

        if(party.getStatus()==3){
            restResultModel.setResult(200);
        }else{
            restResultModel.setResult(400);
            restResultModel.setResult_msg("活动未结束");
        }

        return restResultModel;
    }

    @RequestMapping(value = "/movieAlias", method = RequestMethod.GET)
    public RestResultModel movieAlias() {
        RestResultModel restResultModel = new RestResultModel();
        restResultModel.setResult(200);
        List<Party> partyList = partyService.findOnlineMovie();
        List<MovieAlias> movieAliasList = MovieAlias.getAll();
        List<MovieAlias> temp = new ArrayList<>();
        if( null != movieAliasList){

            for( MovieAlias movieAlias : movieAliasList){
                int i=0;
                for(Party party :partyList ){
                    if( movieAlias.getValue().equals(party.getMovieAlias())){
                        i=1;
                    }
                }
                if( i==0){
                    temp.add(movieAlias);
                }

            }
        }
        restResultModel.setData(temp);
        return restResultModel;
    }

    /**
     * 电影下线
     * @return
     */
    @RequestMapping(value = "/offline", method = RequestMethod.GET)
    public RestResultModel offline(String partyId) {
        RestResultModel restResultModel = new RestResultModel();

        partyService.offlineParty(partyId);

        //删除定时弹幕文件
        bmsTimerDanmuService.deleteTimerFileData(partyId);

        restResultModel.setResult(200);
        return restResultModel;
    }

    @RequestMapping(value = "/currentParty", method = RequestMethod.GET)
    public RestResultModel currentParty(String addressId) {
        PartyLogicModel partyLogicModel = rpcPartyService.findPartyByAddressId(addressId);
        RestResultModel restResultModel = new RestResultModel();
        restResultModel.setData(partyLogicModel);
        return restResultModel;
    }

    @RequestMapping(value = "/partyInfo/{partyId}", method = RequestMethod.GET)
    public RestResultModel partyInfo(@PathVariable("partyId") String partyId) {
        RestResultModel restResultModel = new RestResultModel();
        Party party = partyService.findById(partyId);

        if(party!=null){
            restResultModel.setResult(200);
            restResultModel.setData(party);
        }else{
            restResultModel.setData(0);
        }
        return restResultModel;
    }


    @RequestMapping(value = "/getUpdateParty", method = RequestMethod.GET)
    public RestResultModel getUpdateParty( String partyId) throws InvocationTargetException, IllegalAccessException {
        RestResultModel restResultModel = new RestResultModel();
        UpdatePartyModel updatePartyModel = bmsPartyService.findByPartyId(partyId);
        PartyModel partyModel = new PartyModel();
        BeanUtils.copyProperties(partyModel,updatePartyModel.getParty());
        boolean canUse = false;
        try{
            canUse = rpcPartyService.findCurrentParyIsInProgress(partyModel);
        }catch (Exception e){
            log.error("",e);
        }
        if(null != updatePartyModel){
            if(canUse){
                updatePartyModel.setCanUse(1);
            }else{
                updatePartyModel.setCanUse(0);
            }
            restResultModel.setResult(200);
            restResultModel.setData(updatePartyModel);
        }else{
            restResultModel.setResult(404);
        }
        return restResultModel;
    }








}
