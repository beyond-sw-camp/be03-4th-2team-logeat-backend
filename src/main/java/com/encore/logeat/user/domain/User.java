package com.encore.logeat.user.domain;



import com.encore.logeat.common.entity.BaseTimeEntity;
import com.encore.logeat.follow.domain.Follow;
import com.encore.logeat.post.domain.Post;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;

@Entity
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(unique = true, nullable = false)
	private String email;
	@Column(unique = true, nullable = false, length = 8)
	private String nickName;
	@Column(nullable = false, length = 20)
	private String password;
	@Column(name = "profile_image_path")
	private String profileImagePath;
	@Column(length = 31)
	private String introduce;

	@Enumerated(EnumType.STRING)
	private Role role;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private List<Post> postList;

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private List<Follow> follow;

}