package nextstep.mvc.controller.tobe;

import jakarta.servlet.http.HttpServletRequest;
import nextstep.mvc.HandlerMapping;
import nextstep.mvc.controller.asis.Controller;
import nextstep.web.annotation.RequestMapping;
import nextstep.web.support.RequestMethod;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InterfaceHandlerMapping implements HandlerMapping {

    private static final Logger log = LoggerFactory.getLogger(InterfaceHandlerMapping.class);

    private final Object[] basePackage;
    private final Map<HandlerKey, HandlerExecution> handlerExecutions;

    public InterfaceHandlerMapping(final Object... basePackage) {
        this.basePackage = basePackage;
        this.handlerExecutions = new HashMap<>();
    }

    @Override
    public void initialize() {
        final Reflections reflections = new Reflections(basePackage);
        final Set<Class<? extends Controller>> controllerClasses = reflections.getSubTypesOf(Controller.class);

        for (final Class<?> controller : controllerClasses) {
            final Object controllerInstance = findDefaultConstructor(controller);
            List<Method> requestMappingMethods = findMethodsWithRequestMappingAnnotation(controller);

            for (final Method requestMappingMethod : requestMappingMethods) {
                final RequestMapping requestMapping = requestMappingMethod.getAnnotation(RequestMapping.class);
                final String httpRequestURI = requestMapping.value();
                final RequestMethod[] httpRequestMethods = requestMapping.method();
                for (final RequestMethod httpRequestMethod : httpRequestMethods) {
                    final HandlerKey handlerKey = new HandlerKey(httpRequestURI, httpRequestMethod);
                    final HandlerExecution handlerExecution = HandlerExecution.of(controllerInstance, requestMappingMethod);

                    handlerExecutions.put(handlerKey, handlerExecution);
                }
            }
        }

        log.info("Initialized InterfaceHandlerMapping!");
    }

    private Object findDefaultConstructor(final Class<?> controller) {
        try {
            return controller.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Controller 내에 기본 생성자가 없습니다!");
        }
    }

    private List<Method> findMethodsWithRequestMappingAnnotation(final Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(InterfaceHandlerMapping::hasRequestMappingAnnotation)
                .collect(Collectors.toList());
    }

    private static boolean hasRequestMappingAnnotation(final Method method) {
        return method.isAnnotationPresent(RequestMapping.class);
    }

    @Override
    public Object getHandler(final HttpServletRequest request) {
        final String method = request.getMethod();
        final String requestURI = request.getRequestURI();
        final HandlerKey handlerKey = new HandlerKey(requestURI, RequestMethod.valueOf(method));
        return findHandlerExecution(handlerKey);
    }

    private HandlerExecution findHandlerExecution(final HandlerKey handlerKey) {
        final HandlerExecution handlerExecution = handlerExecutions.getOrDefault(handlerKey, null);
        if (handlerExecution == null) {
            throw new IllegalStateException("해당하는 Handler를 찾을 수 없습니다!");
        }
        return handlerExecution;
    }
}
