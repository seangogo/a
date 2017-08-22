package com.easyRepair.api.interceptor;

import com.easyRepair.service.OperationLogService;
import com.easyRepair.tabEntity.Manager;
import com.easyRepair.tabEntity.OperationLog;
import com.easyRepair.tabEntity.User;
import common.annotation.SystemControllerLog;
import common.annotation.SystemServiceLog;
import common.utils.JsonUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Date;



/**
 * 切点类
 */
@Aspect
@Component
public class SystemLogAspect {
	// 注入Service用于把日志保存数据库
	@Autowired
	private OperationLogService operationLogService;
	// 本地异常日志记录对象
	private static final Logger logger = LoggerFactory.getLogger(SystemLogAspect.class);

	// Service层切点
	@Pointcut("@annotation(common.annotation.SystemServiceLog)")
	public void serviceAspect() {
	}

	// Controller层切点
	@Pointcut("@annotation(common.annotation.SystemControllerLog)")
	public void controllerAspect() {

	}

	/**
	 * 前置通知 用于拦截Controller层记录用户的操作
	 * 
	 * @param joinPoint
	 *            切点
	 */
	@Before("controllerAspect()")
	public void doBefore(JoinPoint joinPoint) {
		StringBuffer params=new StringBuffer();
		for (int i = 0; i < joinPoint.getArgs().length; i++) {
			if (!(joinPoint.getArgs()[i] instanceof HttpServletResponse||joinPoint.getArgs()[i] instanceof HttpServletRequest)){
				params.append(JsonUtil.obj2Json(joinPoint.getArgs()[i]));
			}
		}
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();
		// 读取session中的用户
		Manager manager = (Manager) request.getSession().getAttribute("currentUser");
		
		// 请求的IP
		String ip = getIpAddr(request);
		try {
			// *========控制台输出=========*//
			logger.debug("=====前置通知开始=====");
			logger.debug("请求方法:" + (joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName() + "()"));
			logger.debug("方法描述:" + getControllerMethodDescription(joinPoint));
			logger.debug("请求人id:" + manager.getId());
			logger.debug("请求IP:" + ip);
			// *========数据库日志=========*//
			OperationLog log = new OperationLog();
			log.setDescription(getControllerMethodDescription(joinPoint));
			log.setMethod((joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName() + "()"));
			log.setType(true);
			log.setRequestIp(ip);
			log.setCreater(manager);
			log.setCreateTime(new Date());
			log.setParams(params.toString());
			// 保存数据库
			operationLogService.save(log);
			logger.debug("=====前置通知结束=====");
		} catch (Exception e) {
			// 记录本地异常日志
			logger.error("==前置通知异常==");
			logger.error("异常信息:{}", e.getMessage());
		}
	}

	/**
	 * 异常通知 用于拦截service层记录异常日志
	 * 
	 * @param joinPoint
	 * @param e
	 */
	@AfterThrowing(pointcut = "serviceAspect()||controllerAspect()", throwing = "e")
	public void doAfterThrowing(JoinPoint joinPoint, Throwable e) {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();
		// 读取session中的用户
		Manager manager = (Manager) request.getSession().getAttribute("currentUser");
		// 获取请求ip
		String ip = getIpAddr(request);
		// 获取用户请求方法的参数并序列化为JSON格式字符串
		StringBuffer params=new StringBuffer();
		for (int i = 0; i < joinPoint.getArgs().length; i++) {
			if (!(joinPoint.getArgs()[i] instanceof HttpServletResponse||joinPoint.getArgs()[i] instanceof HttpServletRequest)){
				params.append(JsonUtil.obj2Json(joinPoint.getArgs()[i]));
			}
		}
		try {
			/* ========控制台输出========= */
			logger.debug("=====异常通知开始=====");
			logger.debug("异常代码:" + e.getClass().getName());
			logger.debug("异常信息:" + e.getMessage());
			logger.debug("异常方法:"
					+ (joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName() + "()"));
			logger.debug("方法描述:" + getServiceMthodDescription(joinPoint));
			logger.debug("请求人id:" + manager.getId());
			logger.debug("请求IP:" + ip);
			// logger.debug("请求参数:" + params);
			/* ==========数据库日志========= */
			OperationLog log = new OperationLog();
			log.setDescription(getServiceMthodDescription(joinPoint));
			log.setExceptionCode(e.getClass().getName());
			log.setType(false);
			log.setExceptionDetail(e.getMessage());
			log.setParams(params.toString());
			log.setMethod(
					(joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName() + "()"));
			// log.setParams(params);
			log.setCreater(manager);
			log.setCreateTime(new Date());
			log.setRequestIp(ip);
			// 保存数据库
			operationLogService.save(log);
			logger.debug("=====异常通知结束=====");
		} catch (Exception ex) {
			// 记录本地异常日志
			logger.error("==异常通知异常==");
			//logger.error("异常信息:{}", e.toString());
			logger.error("异常信息:{}", ex.getMessage());
			ex.printStackTrace();
		}
		/* ==========记录本地异常日志========== */
		logger.error("异常方法:{}异常代码:{}异常信息:{}",
				e.getMessage());
	}



	/**
	 * 获取注解中对方法的描述信息 用于service层注解
	 * 
	 * @param joinPoint
	 *            切点
	 * @return 方法描述
	 * @throws Exception
	 */
	public static String getServiceMthodDescription(JoinPoint joinPoint) throws Exception {
		String targetName = joinPoint.getTarget().getClass().getName();
		String methodName = joinPoint.getSignature().getName();
		Object[] arguments = joinPoint.getArgs();
		Class targetClass = Class.forName(targetName);
		Method[] methods = targetClass.getMethods();
		String description = "";
		for (Method method : methods) {
			if (method.getName().equals(methodName)) {
				Class[] clazzs = method.getParameterTypes();
				if (clazzs.length == arguments.length) {
					SystemServiceLog mt = method.getAnnotation(SystemServiceLog.class);
					if(null==mt){

					}else{
						description = mt.description();
					}

					break;
				}
			}
		}
		return description;
	}

	/**
	 * 获取注解中对方法的描述信息 用于Controller层注解
	 * 
	 * @param joinPoint
	 *            切点
	 * @return 方法描述
	 * @throws Exception
	 */
	public static String getControllerMethodDescription(JoinPoint joinPoint) throws Exception {
		String targetName = joinPoint.getTarget().getClass().getName();
		String methodName = joinPoint.getSignature().getName();
		Object[] arguments = joinPoint.getArgs();
		Class targetClass = Class.forName(targetName);
		Method[] methods = targetClass.getMethods();
		String description = "";
		for (Method method : methods) {
			if (method.getName().equals(methodName)) {
				Class[] clazzs = method.getParameterTypes();
				if (clazzs.length == arguments.length) {
					description = method.getAnnotation(SystemControllerLog.class).description();
					break;
				}
			}
		}
		return description;
	}

	/**
	 * 获取ip
	 * 
	 * @param request
	 * @return
	 */
	public String getIpAddr(HttpServletRequest request) {
		String ip = request.getHeader("x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}
}
