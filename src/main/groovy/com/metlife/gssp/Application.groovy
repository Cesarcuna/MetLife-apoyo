package com.metlife.gssp

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import com.metlife.gssp.configuration.EnableGSSPService

@SpringBootApplication
@ComponentScan(basePackages="com.metlife.eos")
@EnableGSSPService
class Application {

	static void main(String[] args) {
		SpringApplication.run(Application, args)
	}

}
