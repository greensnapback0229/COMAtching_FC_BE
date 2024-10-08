package comatchingfc.comatchingfc.auth.service;

import comatchingfc.comatchingfc.auth.TokenUtil;
import comatchingfc.comatchingfc.auth.dto.Res.UserLoginRes;
import comatchingfc.comatchingfc.auth.dto.req.UserLoginReq;
import comatchingfc.comatchingfc.auth.enums.TicketType;
import comatchingfc.comatchingfc.auth.jwt.dto.TokenRes;
import comatchingfc.comatchingfc.exception.BusinessException;
import comatchingfc.comatchingfc.user.entity.UserAiInfo;
import comatchingfc.comatchingfc.user.entity.UserFeature;
import comatchingfc.comatchingfc.user.entity.Users;
import comatchingfc.comatchingfc.user.enums.TeamSide;
import comatchingfc.comatchingfc.user.repository.UserAiInfoRepository;
import comatchingfc.comatchingfc.user.repository.UserFeatureRepository;
import comatchingfc.comatchingfc.user.repository.UserRepository;
import comatchingfc.comatchingfc.utils.rabbitMQ.AuthRabbitMQUtil;
import comatchingfc.comatchingfc.utils.rabbitMQ.Message.res.ReserveAuthResMsg;
import comatchingfc.comatchingfc.utils.response.ResponseCode;
import comatchingfc.comatchingfc.utils.uuid.UUIDUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserAiInfoRepository userAiInfoRepository;
    private final UserFeatureRepository userFeatureRepository;
    private final AuthRabbitMQUtil authRabbitMQUtil;
    private final TokenUtil tokenUtil;

    @Transactional
    public UserLoginRes userLogin(UserLoginReq userLoginReq) {
        String type = userLoginReq.getType();
        TicketType ticketType = type.equals(TicketType.online.toString()) ? TicketType.online : TicketType.offline;

        if (ticketType.equals(TicketType.offline)) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        ReserveAuthResMsg reserveAuthResMsg = authRabbitMQUtil.checkReserveNumber(userLoginReq.getTicket());
        if(!reserveAuthResMsg.isAuthSuccess()){
            throw new BusinessException(ResponseCode.INVALID_TICKET);
        }

        Optional<Users> userOpt = userRepository.findByIdentifyKey(userLoginReq.getTicket());
        String userUuid;
        String userRole;

        if (userOpt.isEmpty()) {
            Users user = Users.builder()
                    .identifyKey(userLoginReq.getTicket())
                    .build();

            UserAiInfo userAiInfo = UserAiInfo.builder()
                    .uuid(UUIDUtil.createUUID())
                    .build();

            UserFeature userFeature = UserFeature.builder().build();

            userAiInfo.setUsers(user);
            userFeature.setUserAiInfo(userAiInfo);

            userRepository.save(user);
            userAiInfoRepository.save(userAiInfo);
            userFeatureRepository.save(userFeature);

            userUuid = UUIDUtil.bytesToHex(userAiInfo.getUuid());
            userRole = user.getRole().toString();

        } else {
            Users user = userOpt.get();
            if (user.getDeactivated()) {
                throw new BusinessException(ResponseCode.DEACTIVATED_USER);
            }
            userUuid = UUIDUtil.bytesToHex(user.getUserAiInfo().getUuid());
            userRole = user.getRole().toString();
        }

        TokenRes tokenRes = tokenUtil.makeTokenRes(userUuid, userRole);

//        return new UserLoginRes(reserveAuthResMsg.getTeamSide(), tokenRes);
        return new UserLoginRes(TeamSide.HOME, userRole, tokenRes);
    }
}
