package com.deloitte.returns.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.deloitte.common.bean.GSTAuthenticationInputBean;
import com.deloitte.common.bean.GSTAuthenticationResponseBean;
import com.deloitte.common.constant.Constants;
import com.deloitte.common.entity.APIDetails;
import com.deloitte.common.entity.GSTUserSession;
import com.deloitte.common.entity.MasterData;
import com.deloitte.returns.repository.common.GSTUserSessionRepository;
import com.deloitte.service.impl.APIDetailsImpl;
import com.deloitte.service.support.AESEncryption;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class GstUserSessionServices {

	@Autowired
	private GSTUserSessionRepository gSTUserSessionRepository;

	@Autowired
	private APIDetailsImpl apiDetailsImpl;

	@Autowired
	private MasterDataService masterDataService;

	@Autowired
	private GstUserSessionServicesSupport gstUserSessionServicesSupport;

	public GSTUserSession getUserSessionsByName(String username) {
		GSTUserSession gstUserSession = null;

		List<GSTUserSession> gstUserSessions = gSTUserSessionRepository
				.findByUserNameOrderByCreateDateTimeDesc(username);
		if (gstUserSessions.isEmpty()) {
			gstUserSession = createUserSession(username);
		} else {
			gstUserSession = gstUserSessions.stream().findFirst().get();
		}
		return validateSeesion(gstUserSession);
	}

	public GSTUserSession createUserSession(String username) {
		GSTUserSession gSTUserSession = null;
		APIDetails apiDetails = apiDetailsImpl.findByName(Constants.COMMON_AUTH);
		if (apiDetails != null) {
			gSTUserSession = gSTUserSessionRepository.save(getUserSession(username, apiDetails));
		}
		return gSTUserSession;
	}

	public GSTUserSession validateSeesion(GSTUserSession gstUserSession) {
		GSTUserSession validGstUserSession = null;
		if (null != gstUserSession && LocalDateTime.now().isAfter(gstUserSession.getAuthDateTime().plusHours(3))) {
			deleteUserSession(gstUserSession.getId());
			validGstUserSession = createUserSession(gstUserSession.getUserName());
		} else {
			validGstUserSession = gstUserSession;
		}
		return validGstUserSession;
	}

	public void deleteUserSession(Long Id) {
		gSTUserSessionRepository.deleteById(Id);
	}

	public GSTUserSession getUserSession(String username, APIDetails apiDetails) {

		GSTUserSession gstUserSession = new GSTUserSession();
		gstUserSession.setUserName(username);

		MasterData masterData = masterDataService.getMasterdatabyName(username);

		if (masterData == null) {
			log.error("User {} not found in Master Data table", username);
			return null; // ya throw new IllegalStateException(...) if preferred
		}

		String appKey = AESEncryption.getAppkey();
		gstUserSession.setAppKey(appKey);

		String encryAppKey = AESEncryption.getEncryptedAppKey(appKey);
		String encryptedPassword = AESEncryption.getEncryptedPassword(masterData.getPassword());

		log.debug("AppKey :{}", appKey);
		log.debug("Encrypted AppKey :{}", encryAppKey);
		log.debug("Encrypted Password :{}", encryptedPassword);

		createRequestBodyForAuth(encryAppKey, encryptedPassword, gstUserSession, masterData, apiDetails);

		return gstUserSession;
	}

	private void createRequestBodyForAuth(String appKey, String encryptedPassword, GSTUserSession gSTUserSession,
			MasterData masterData, APIDetails apiDetails) {
		GSTAuthenticationInputBean gstAuthenticationInputBean = new GSTAuthenticationInputBean();
		gstAuthenticationInputBean.setAction(apiDetails.getApiAction());
		gstAuthenticationInputBean.setUsername(gSTUserSession.getUserName());
		gstAuthenticationInputBean.setPassword(encryptedPassword);
		gstAuthenticationInputBean.setAppKey(appKey);

		GSTAuthenticationResponseBean gstAuthenticationResponseBean = gstUserSessionServicesSupport.doAuth(apiDetails,
				gstAuthenticationInputBean, masterData);

		if (null != gstAuthenticationResponseBean && gstAuthenticationResponseBean.getStatus_cd().equals("0")) {
			
			log.error("Exception thrown from GSTN server while authenticating user session "
					+ gstAuthenticationResponseBean.getError().get("message"));
		} else {
			assert gstAuthenticationResponseBean != null;
			gSTUserSession.setSek(gstAuthenticationResponseBean.getSek());
			gSTUserSession.setAuthToken(gstAuthenticationResponseBean.getAuth_token());
		
			gSTUserSessionRepository.save(gSTUserSession);
		}



	}

	public boolean isSessionExpired(String username) {

		List<GSTUserSession> gstUserSessions = gSTUserSessionRepository
				.findByUserNameOrderByCreateDateTimeDesc(username);

		// No session found
		if (gstUserSessions == null || gstUserSessions.isEmpty()) {
			return true;
		}

		// Latest session
		GSTUserSession session = gstUserSessions.get(0);

		// Session validation
		return session.getAuthDateTime() == null
				|| session.getAuthDateTime().plusHours(6).isBefore(LocalDateTime.now());
	}



}
