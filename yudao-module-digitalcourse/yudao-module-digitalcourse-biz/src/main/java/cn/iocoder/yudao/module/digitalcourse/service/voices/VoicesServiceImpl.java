package cn.iocoder.yudao.module.digitalcourse.service.voices;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.digitalcourse.controller.admin.voices.vo.VoicesPageReqVO;
import cn.iocoder.yudao.module.digitalcourse.controller.admin.voices.vo.VoicesSaveReqVO;
import cn.iocoder.yudao.module.digitalcourse.dal.dataobject.voices.AuditionDO;
import cn.iocoder.yudao.module.digitalcourse.dal.dataobject.voices.TTSDTO;
import cn.iocoder.yudao.module.digitalcourse.dal.dataobject.voices.VoicesDO;
import cn.iocoder.yudao.module.digitalcourse.dal.mysql.voices.VoicesMapper;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.FileOutputStream;
import java.io.IOException;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.digitalcourse.enums.ErrorCodeConstants.VOICES_NOT_EXISTS;

/**
 * 声音管理 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class VoicesServiceImpl implements VoicesService {

    private static final String EASEGEN_CORE_URL = "easegen.core.url";
    static final String EASEGEN_CORE_KEY = "easegen.core.key";

    @Resource
    private VoicesMapper voicesMapper;

    @Resource
    private FileApi fileApi;

    @Resource
    private ConfigApi configApi;

    @Override
    public Long createVoices(VoicesSaveReqVO createReqVO) {
        // 插入
        VoicesDO voices = BeanUtils.toBean(createReqVO, VoicesDO.class);
        voicesMapper.insert(voices);
        // 返回
        return voices.getId();
    }

    @Override
    public void updateVoices(VoicesSaveReqVO updateReqVO) {
        // 校验存在
        validateVoicesExists(updateReqVO.getId());
        // 更新
        VoicesDO updateObj = BeanUtils.toBean(updateReqVO, VoicesDO.class);
        voicesMapper.updateById(updateObj);
    }

    @Override
    public void deleteVoices(Long id) {
        // 校验存在
        validateVoicesExists(id);
        // 删除
        voicesMapper.deleteById(id);
    }

    private void validateVoicesExists(Long id) {
        if (voicesMapper.selectById(id) == null) {
            throw exception(VOICES_NOT_EXISTS);
        }
    }

    @Override
    public VoicesDO getVoices(Long id) {
        return voicesMapper.selectById(id);
    }

    @Override
    public PageResult<VoicesDO> getVoicesPage(VoicesPageReqVO pageReqVO) {
        return voicesMapper.selectPage(pageReqVO);
    }

    @Override
    public String audition(AuditionDO auditionDO) {
        VoicesDO voice = voicesMapper.selectById(auditionDO.getVoiceId());
        TTSDTO ttsdto = new TTSDTO();
        if (voice != null) {
            ttsdto.setModel_code(voice.getCode());
            ttsdto.setSentence(auditionDO.getText());
            ttsdto.setRequest_id(StrUtil.uuid());
            ttsdto.setUser_id(String.valueOf(SecurityFrameworkUtils.getLoginUser().getId()));
            ttsdto.setVoice_type(String.valueOf(voice.getVoiceType()));
        } else {
            throw exception(VOICES_NOT_EXISTS);
        }

        String apiUrl = configApi.getConfigValueByKey(EASEGEN_CORE_URL)+"/api/tts";
        String apiKey = configApi.getConfigValueByKey(EASEGEN_CORE_KEY);
        // 创建HTTP客户端
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 创建POST请求
            HttpPost httpPost = new HttpPost(apiUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("X-API-Key", apiKey);
            httpPost.setEntity(new StringEntity(JSON.toJSONString(ttsdto), ContentType.APPLICATION_JSON));
            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();
                String responseString = EntityUtils.toString(response.getEntity());
                if (statusCode == 200) {
                    // 解析JSON响应
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonResponse = objectMapper.readTree(responseString);
                    String audioBase64 = jsonResponse.get("audio").asText();
                    // 解码Base64音频字符串
                    byte[] audioBytes = Base64.decodeBase64(audioBase64);
                    // 保存音频文件
                    return fileApi.createFile("output.wav", null, audioBytes);
//                    try (FileOutputStream fos = new FileOutputStream("output.wav")) {
//                        fos.write(audioBytes);
//                        String path = fileApi.createFile("output.wav", null, audioBytes);
//                        return path;
//                    }
                } else {
                    System.out.println("Error: " + responseString);
                }
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

}