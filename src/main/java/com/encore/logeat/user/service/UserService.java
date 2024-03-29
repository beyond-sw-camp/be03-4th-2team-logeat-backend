package com.encore.logeat.user.service;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.encore.logeat.common.dto.ResponseDto;
import com.encore.logeat.common.jwt.JwtTokenProvider;
import com.encore.logeat.common.s3.S3Config;
import com.encore.logeat.mail.service.EmailService;
import com.encore.logeat.common.jwt.refresh.UserRefreshToken;
import com.encore.logeat.common.jwt.refresh.UserRefreshTokenRepository;
import com.encore.logeat.user.domain.User;
import com.encore.logeat.user.dto.request.UserCreateRequestDto;
import com.encore.logeat.user.dto.response.UserInfoResponseDto;
import com.encore.logeat.user.dto.request.UserInfoUpdateRequestDto;
import com.encore.logeat.user.dto.request.UserLoginRequestDto;
import com.encore.logeat.user.repository.UserRepository;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final EmailService emailService;
	private final S3Config s3Config;
	private final UserRefreshTokenRepository userRefreshTokenRepository;

	@Value("${cloud.aws.s3.bucket}")
	private String bucket;

	@Autowired
	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
		JwtTokenProvider jwtTokenProvider, EmailService emailService,
		UserRefreshTokenRepository userRefreshTokenRepository,
		S3Config s3Config) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.s3Config = s3Config;
		this.emailService = emailService;
		this.userRefreshTokenRepository = userRefreshTokenRepository;
	}

	@Transactional
	public User createUser(UserCreateRequestDto userCreateRequestDto) {
		//emailService.createEmailAuthNumber(userCreateRequestDto.getEmail());

		userCreateRequestDto.setPassword(
			passwordEncoder.encode(userCreateRequestDto.getPassword()));
		User user = userCreateRequestDto.toEntity();
		// 유저 프로필 이미지 추가해주는 로직 작성 필요
		return userRepository.save(user);
	}

	@Transactional
	public ResponseDto userLogin(UserLoginRequestDto userLoginRequestDto) {
		User user = userRepository.findByEmail(userLoginRequestDto.getEmail())
			.filter(
				it -> passwordEncoder.matches(userLoginRequestDto.getPassword(), it.getPassword()))
			.orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));
		String accessToken = jwtTokenProvider.createAccessToken(
			String.format("%s:%s", user.getId(), user.getRole()));
		String refreshToken = jwtTokenProvider.createRefreshToken();
		userRefreshTokenRepository.findById(user.getId())
			.ifPresentOrElse(
				it -> it.updateUserRefreshToken(refreshToken),
				() -> userRefreshTokenRepository.save(new UserRefreshToken(user, refreshToken))
			);
		Map<String, String> result = new HashMap<>();
		result.put("access_token", accessToken);
		result.put("refresh_token", refreshToken);
		return new ResponseDto(HttpStatus.OK, "JWT token is created!", result);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasAuthority('USER')")
	public ResponseDto getMyFollower() {
		String name = SecurityContextHolder.getContext().getAuthentication().getName();
		String[] split = name.split(":");
		long userId = Long.parseLong(split[0]);

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("예기치 못한 에러가 발생하였습니다."));
		List<String> result = user.getFollowerList().stream().map(follow -> {
			return follow.getFollower().getNickname();
		}).collect(Collectors.toList());
		return new ResponseDto(HttpStatus.OK, "팔로워의 수는 " + result.size() + "입니다.", result);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasAuthority('USER')")
	public ResponseDto getMyFollowing() {
		String name = SecurityContextHolder.getContext().getAuthentication().getName();
		String[] split = name.split(":");
		long userId = Long.parseLong(split[0]);

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("예기치 못한 에러가 발생하였습니다."));
		List<String> result = user.getFollowingList().stream().map(follow -> {
			return follow.getFollowing().getNickname();
		}).collect(Collectors.toList());
		return new ResponseDto(HttpStatus.OK, "팔로우 하고 있는 유저의 수는 " + result.size() + "입니다.", result);
	}

	public boolean nicknameDuplicateCheck(String nickname) {
		return userRepository.existsByNickname(nickname);
	}

	public boolean emailDuplicateCheck(String email) {
		return userRepository.existsByEmail(email);
	}

	@Transactional
	@PreAuthorize("hasAuthority('USER')")
	public void updateInfoUser(UserInfoUpdateRequestDto userInfoupdateDto) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String currentUserName = authentication.getName();
		Long userId = Long.parseLong(currentUserName);
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new EntityNotFoundException("유저의 아이디를 찾을 수 없습니다. " + userId));
		user.updateUserInfo(userInfoupdateDto.getNickname(), userInfoupdateDto.getIntroduce());
	}

	@PreAuthorize("hasAuthority('USER')")
	public UserInfoResponseDto getMypage() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String currentUserName = authentication.getName();
		Long userId = Long.parseLong(currentUserName);
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new EntityNotFoundException("유저의 아이디를 찾을 수 없습니다. " + userId));
		UserInfoResponseDto userInfo = new UserInfoResponseDto();
		userInfo.setNickname(user.getNickname());
		userInfo.setImageUrl(user.getProfileImagePath());
		userInfo.setIntroduce(user.getIntroduce());

		return userInfo;

	}

	public String saveFile(MultipartFile request, String newFileName) throws IOException {
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(request.getSize());
		metadata.setContentType(request.getContentType());

		s3Config.amazonS3Client()
			.putObject(bucket, newFileName, request.getInputStream(), metadata);
		return s3Config.amazonS3Client().getUrl(bucket, newFileName).toString();
	}

	@PreAuthorize("hasAuthority('USER')")
	@Transactional
	public void updateUserImage(String imageUrl) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String currentUserName = authentication.getName();
		Long userId = Long.parseLong(currentUserName);

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new EntityNotFoundException("유저의 아이디를 찾을 수 없습니다. " + userId));

		user.userUpdatedProfileImageUrl(imageUrl);
		userRepository.save(user);
	}

//	@Transactional
//	public ResponseEntity<?> updatePassword(String emailAuthNumber, String email, String changePwd) {
//		Boolean b = emailService.verificationEmailAuth(email, emailAuthNumber);
//
//		String message = "";
//		if(b) {
//			User findUser = userRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("아이디가 없습니다."));
//			String encode = passwordEncoder.encode(changePwd);
//			findUser.updatedPassword(encode);
//			message = "비밀번호가 변경되었습니다.";
//		}else {
//			message = "인증이 만료되었습니다. 다시 설정해주시길 바랍니다.";
//		}
//
//		return ResponseEntity.ok()
//				.body(new ResponseDto(HttpStatus.OK, message, findUser.getEmail()));
//	}


}
