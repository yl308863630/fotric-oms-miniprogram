package com.oms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kuaidi100")
public class KuaiDi100Config {
    private String appKey = "ZuBYFiIT2183";
    private String customer = "4F9557DCDA6E36F61B64E14212D25F7D";
    private String apiUrl = "https://poll.kuaidi100.com/poll/query.do";
    private String sfAppKey = "FCKZH1AEF3YE";
    private String sfAppSecret = "VBYxDKKlufvu7mui7ONaVxcINnLFcf2m";
    private String sfProdAppSecret = "8lFzNnLpeDNftGCfauWq2D3ilTCxzh0i";
    private String sfApiUrl = "https://sfapi.sf-express.com/std/service";
    private String jdAppKey = "2292f881a51b4a65843e378a696c0198";
    private String jdAppSecret = "ecfb74cbd394492b90652cf082b8c0f3";
    private String jdApiUrl = "https://api.jdl.com";
    private Boolean enableMock = false;
    private Boolean useSfProd = false;
}
