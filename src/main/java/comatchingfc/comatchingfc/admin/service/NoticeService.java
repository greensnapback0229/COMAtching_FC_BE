package comatchingfc.comatchingfc.admin.service;

import org.springframework.stereotype.Service;

import comatchingfc.comatchingfc.admin.dto.req.NoticeRegisterReq;
import comatchingfc.comatchingfc.admin.entity.Notice;
import comatchingfc.comatchingfc.admin.repository.NoticeRepository;
import comatchingfc.comatchingfc.utils.security.SecurityUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NoticeService {

	private final SecurityUtil securityUtil;
	private final NoticeRepository noticeRepository;
	public void registerNotice(NoticeRegisterReq noticeAddReq){
		Notice notice= Notice.builder()
			.body(noticeAddReq.getBody())
			.expireDate(noticeAddReq.getExpireDate())
			.admin(securityUtil.getCurrentAdminEntity())
			.build();

		noticeRepository.save(notice);
	}


}
