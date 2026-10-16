package io.github.artemiscore.artemisappsec.organization.api;

import java.util.List;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.github.artemiscore.artemisappsec.organization.service.MembershipService;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;

/** Controllers receive the request's organization as a {@link Tenant} parameter. */
@Configuration
class TenantWebConfig implements WebMvcConfigurer {

	static {
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(Tenant.class);
	}

	private final MembershipService memberships;
	private final boolean authEnabled;

	TenantWebConfig(MembershipService memberships, ArtemisAppSecProperties properties) {
		this.memberships = memberships;
		this.authEnabled = properties.security().authEnabled();
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new TenantInterceptor(memberships, authEnabled)).addPathPatterns("/api/v1/**");
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == Tenant.class;
			}

			@Override
			public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
					NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
				Object tenant = webRequest.getAttribute(TenantInterceptor.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
				if (tenant == null) {
					throw new IllegalStateException("No organization was resolved for " + parameter.getExecutable());
				}
				return tenant;
			}
		});
	}

	@Bean
	OperationCustomizer organizationHeader() {
		return (operation, handlerMethod) -> {
			if (TenantInterceptor.isOrganizationScoped(handlerMethod)) {
				operation.addParametersItem(new HeaderParameter().name(TenantInterceptor.HEADER).required(true)
					.description("Slug of the organization the request acts on").schema(new StringSchema()));
			}
			return operation;
		};
	}
}
