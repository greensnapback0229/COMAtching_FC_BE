package comatchingfc.comatchingfc.match.service;

import static comatchingfc.comatchingfc.user.enums.Gender.*;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import comatchingfc.comatchingfc.exception.BusinessException;
import comatchingfc.comatchingfc.match.dto.req.MatchReq;
import comatchingfc.comatchingfc.match.dto.res.MatchRes;
import comatchingfc.comatchingfc.match.entity.MatchingHistory;
import comatchingfc.comatchingfc.match.repository.MatchingHistoryRepository;
import comatchingfc.comatchingfc.user.entity.CheerPropensity;
import comatchingfc.comatchingfc.user.entity.UserAiInfo;
import comatchingfc.comatchingfc.user.entity.UserFeature;
import comatchingfc.comatchingfc.user.entity.Users;
import comatchingfc.comatchingfc.user.enums.CheerPropensityEnum;
import comatchingfc.comatchingfc.user.enums.Gender;
import comatchingfc.comatchingfc.user.repository.UserAiInfoRepository;
import comatchingfc.comatchingfc.user.repository.UserFeatureRepository;
import comatchingfc.comatchingfc.user.repository.UserRepository;
import comatchingfc.comatchingfc.utils.rabbitMQ.MatchingRabbitMQUtil;
import comatchingfc.comatchingfc.utils.rabbitMQ.Message.req.MatchReqMsg;
import comatchingfc.comatchingfc.utils.rabbitMQ.Message.res.MatchResMsg;
import comatchingfc.comatchingfc.utils.response.ResponseCode;
import comatchingfc.comatchingfc.utils.security.SecurityUtil;
import comatchingfc.comatchingfc.utils.uuid.UUIDUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchService {
	private final UserRepository userRepository;
	private final UserFeatureRepository userFeatureRepository;
	private final UserAiInfoRepository userAiInfoRepository;
	private final MatchingRabbitMQUtil matchingRabbitMQUtil;
	private final MatchingHistoryRepository matchingHistoryRepository;
	private final SecurityUtil securityUtil;

	/**
	 * 매칭 프로세스 및 matching history 생성
	 * @param matchReq 이성 옵션
	 * @return 매칭 결과
	 */
	@Transactional
	public MatchRes requestMatch(MatchReq matchReq){
		Users applier = securityUtil.getCurrentUserEntity();
		UserAiInfo applierAiInfo = applier.getUserAiInfo();
		UserFeature applierFeature = applierAiInfo.getUserFeature();
		List<CheerPropensity> applierCheerPropensities = applierFeature.getCheerPropensities();

		boolean lackOfResource = checkLackOfResource(matchReq.getGenderOption(), applierFeature.getAge(), applierFeature.getPropensity());

		MatchReqMsg matchReqMsg = new MatchReqMsg(applierFeature, applierAiInfo, applierCheerPropensities);
		MatchResMsg matchResMsg = matchingRabbitMQUtil.requestMatch(matchReqMsg);

		UserAiInfo enemyAiInfo = userAiInfoRepository.findByUuid(UUIDUtil.uuidStringToBytes(matchResMsg.getEnemyUuid()))
			.orElseThrow(() -> new BusinessException(ResponseCode.ENEMY_NOT_FOUND));

		Users enemy = enemyAiInfo.getUsers();
		UserFeature enemyFeature = enemyAiInfo.getUserFeature();

		MatchingHistory matchingHistory = MatchingHistory.builder()
			.applier(applier)
			.enemy(enemyAiInfo.getUsers())
			.build();

		matchingHistoryRepository.save(matchingHistory);

		return MatchRes.builder()
			.age(enemyFeature.getAge())
			.gender(enemyFeature.getGender())
			.username(enemy.getUsername())
			.propensity(enemyFeature.getPropensity())
			.cheeringPlayer(enemy.getCheeringPlayer())
			.socialId(enemy.getSocialId())
			.lackOfResource(lackOfResource)
			.build();
	}

	private boolean checkLackOfResource(Gender genderOption, int age, CheerPropensityEnum propensityOption){
		long count = 0;
		if(genderOption.equals(NONE)){
			count = userFeatureRepository.countMatchableUserAndPropensityAndAge(propensityOption.getValue(), age);
		}
		else{
			count = userFeatureRepository.countMatchableUserByGenderAndPropensityAndAge(genderOption.getValue(),
				propensityOption.getValue(), age);
		}

		return (count == 0)? true : false;
	}
}
