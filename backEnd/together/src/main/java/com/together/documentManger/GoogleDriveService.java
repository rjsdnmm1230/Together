package com.together.documentManger;

import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.together.project.ProjectEntity;
import com.together.project.ProjectRepository;
import com.together.user.UserEntity;
import com.together.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private final Drive googleDrive;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    // 파일 업로드 후 공개 설정
    private void makeFilePublic(String fileId) throws IOException {
        Permission permission = new Permission();
        permission.setType("anyone");  // 모든 사용자 허용
        permission.setRole("reader");  // 읽기 전용 권한 부여

        googleDrive.permissions()
                .create(fileId, permission)
                .execute();
    }

    // ✅ 파일 업로드 (Google Drive + DB 저장)
    public FileEntity uploadFile(MultipartFile file, Long userId, Long projectId) throws IOException {
        // 🔍 사용자와 프로젝트 조회
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));

        // 📁 Google Drive에 업로드할 메타데이터 설정
        File fileMetadata = new File();
        fileMetadata.setName(file.getOriginalFilename());
        fileMetadata.setParents(List.of("root")); // 루트 폴더에 저장

        // 🧠 실제 파일 생성
        java.io.File tempFile = java.io.File.createTempFile("upload_", null);
        try (OutputStream os = new FileOutputStream(tempFile)) {
            os.write(file.getBytes());
        }

        // 📌 MIME 타입 자동 감지 (ex. image/jpeg, application/pdf ...)
        String mimeType = file.getContentType();
        AbstractInputStreamContent fileContent = new FileContent(mimeType, tempFile);

        // ☁️ Google Drive로 파일 업로드
        File uploadedFile = googleDrive.files().create(fileMetadata, fileContent)
                .setFields("id, webViewLink, mimeType, size")
                .execute();

        // 🌍 업로드한 파일을 공개로 설정
        makeFilePublic(uploadedFile.getId());

        // 🗃️ DB에 파일 메타데이터 저장
        FileEntity fileEntity = FileEntity.builder()
                .googleDriveFileId(uploadedFile.getId())
                .fileName(file.getOriginalFilename())
                .fileType(uploadedFile.getMimeType())   // 예: image/jpeg
                .fileSize(String.valueOf(uploadedFile.getSize()))
                .fileUrl(uploadedFile.getWebViewLink())
                .user(user)
                .project(project)
                .build();

        return fileRepository.save(fileEntity);
    }

    // **특정 프로젝트의 모든 파일 조회**
    public List<FileEntity> getFilesByProject(Long projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
        return fileRepository.findByProject(project);
    }

    // **파일 삭제 (Google Drive + DB에서 삭제)**
    public void deleteFile(String googleDriveFileId) throws IOException {
        googleDrive.files().delete(googleDriveFileId).execute();
        fileRepository.findByGoogleDriveFileId(googleDriveFileId)
                .ifPresent(fileRepository::delete);
    }

    // 파일 다운로드
    public ByteArrayResource downloadFile(String fileId) throws IOException {
        // Google Drive에서 파일 가져오기
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        googleDrive.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream);

        // 바이트 배열을 리소스로 변환
        return new ByteArrayResource(outputStream.toByteArray());
    }

    // Google Drive에 이미지만 업로드하고 URL 반환
    public String uploadImageToGoogleDrive(MultipartFile file) throws IOException {
        // **Google Drive에 파일 업로드**
        File fileMetadata = new File();
        fileMetadata.setName(file.getOriginalFilename());
        fileMetadata.setParents(List.of("root")); // 루트 폴더에 저장

        java.io.File tempFile = java.io.File.createTempFile("upload_", null);
        try (OutputStream os = new FileOutputStream(tempFile)) {
            os.write(file.getBytes());
        }

        AbstractInputStreamContent fileContent = new FileContent("application/octet-stream", tempFile);
        File uploadedFile = googleDrive.files().create(fileMetadata, fileContent)
                .setFields("id, webViewLink")
                .execute();

        // 🔹 파일을 공개로 설정
        makeFilePublic(uploadedFile.getId());

        // 🔹 Google Drive URL 반환
        return uploadedFile.getWebViewLink();
    }

    public String extractDriveFileId(String fileUrl) {
        // 예: https://drive.google.com/file/d/FILE_ID/view?usp=sharing
        String regex = "/d/([a-zA-Z0-9_-]+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(fileUrl);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new IllegalArgumentException("잘못된 구글 드라이브 URL 형식입니다: " + fileUrl);
        }
    }


}
