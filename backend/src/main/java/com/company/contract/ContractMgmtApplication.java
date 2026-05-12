package com.company.contract;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.company.contract.mapper")
public class ContractMgmtApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractMgmtApplication.class, args);
    }
}
