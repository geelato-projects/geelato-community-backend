package cn.geelato.web.oss.ali;

import cn.geelato.web.oss.*;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;


public class AliFileObjectSrvProvider implements FileObjectSrvProvider {
    private static final Logger log = LoggerFactory.getLogger(AliFileObjectSrvProvider.class);
    private static final String __FileNameMetaKey__="fileName";
    private final OSS ossClient;
    private final AliOSSConfiguration aliOSSConfiguration;
    public AliFileObjectSrvProvider(AliOSSConfiguration aliOSSConfiguration){
        this.aliOSSConfiguration=aliOSSConfiguration;
        CredentialsProvider credentialsProvider = CredentialsProviderFactory.newDefaultCredentialProvider(
                aliOSSConfiguration.getAccessKeyId(),
                aliOSSConfiguration.getAccessKeySecret()
        );
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        ossClient = OSSClientBuilder.create()
                .endpoint(aliOSSConfiguration.getEndPoint())
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(aliOSSConfiguration.getRegion())
                .build();
    }


    @Override
    public OSSResult putFile(FileMeta fileMeta) {
        OSSResult ossResult=new OSSResult();
        String objectName;
        if(aliOSSConfiguration.getGenerateObjectNameFn()!=null){
            objectName=aliOSSConfiguration.getGenerateObjectNameFn().generateObjectName(fileMeta);
        }else{
            objectName=fileMeta.getFileName();
        }
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(aliOSSConfiguration.getBucketName(),
                    objectName ,
                    fileMeta.getFileInputStream());
            ObjectMetadata objectMetadata=new ObjectMetadata();
            objectMetadata.setUserMetadata(Map.of(__FileNameMetaKey__,fileMeta.getFileName()));
            putObjectRequest.setMetadata(objectMetadata);
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            if(putObjectResult.getETag()!=null){
                OSSFile ossFile=new OSSFile().setFileMeta(fileMeta)
                        .setObjectId(putObjectResult.getETag())
                        .setObjectName(objectName);
                ossResult.setOssFile(ossFile);
            }else{
                ossResult.setSuccess(false);
                ossResult.setMessage(putObjectResult.getResponse().getErrorResponseAsString());
            }
            return ossResult;
        } catch (OSSException oe) {
            ossResult.setSuccess(false);
            ossResult.setMessage(oe.getMessage());
            return ossResult;
        }
    }

    @Override
    public OSSResult getFile(String objectName) {
        OSSResult ossResult=new OSSResult();
        try{
            OSSObject ossObject = ossClient.getObject(aliOSSConfiguration.getBucketName(), objectName);
            String fileName;
            if(ossObject.getObjectMetadata()!=null&&ossObject.getObjectMetadata().getUserMetadata()!=null){
                fileName= ossObject.getObjectMetadata().getUserMetadata().get(__FileNameMetaKey__);
            }else{
                fileName=objectName;
            }
            InputStream inputStream = ossObject.getObjectContent();
            FileMeta fileMeta=new FileMeta(fileName,inputStream);
            OSSFile ossFile=new OSSFile().setFileMeta(fileMeta).setObjectName(objectName);
            ossResult.setSuccess(true);
            ossResult.setOssFile(ossFile);
        } catch (OSSException ex) {
            ossResult.setSuccess(false);
            ossResult.setMessage(ex.getMessage());
        }
        return ossResult;
    }

    @Override
    public OSSResult getFiles(List<String> objectNameList) {
        OSSResult ossResult = new OSSResult();
        List<OSSFile> ossFiles = new java.util.ArrayList<>();
        List<String> failedKeys = new java.util.ArrayList<>();
        StringBuilder errorMsg = new StringBuilder();

        for (String objectName : objectNameList) {
            try {
                OSSObject ossObject = ossClient.getObject(aliOSSConfiguration.getBucketName(), objectName);
                String fileName;
                if (ossObject.getObjectMetadata() != null && ossObject.getObjectMetadata().getUserMetadata() != null) {
                    fileName = ossObject.getObjectMetadata().getUserMetadata().get(__FileNameMetaKey__);
                } else {
                    fileName = objectName;
                }
                InputStream inputStream = ossObject.getObjectContent();
                FileMeta fileMeta = new FileMeta(fileName, inputStream);
                OSSFile ossFile = new OSSFile().setFileMeta(fileMeta).setObjectName(objectName);
                ossFiles.add(ossFile);
            } catch (OSSException ex) {
                failedKeys.add(objectName);
                log.warn("批量获取 OSS 对象失败: objectName={}, error={}", objectName, ex.getMessage());
                if (errorMsg.length() > 0) errorMsg.append("; ");
                errorMsg.append(objectName).append(": ").append(ex.getMessage());
            }
        }

        ossResult.setOssFileList(ossFiles);
        if (failedKeys.isEmpty()) {
            ossResult.setSuccess(true);
        } else {
            ossResult.setSuccess(false);
            ossResult.setMessage("部分对象获取失败: " + errorMsg);
        }
        return ossResult;
    }

    @Override
    public OSSResult removeFile(String objectName) {
        OSSResult ossResult = new OSSResult();
        try {
            ossClient.deleteObject(aliOSSConfiguration.getBucketName(), objectName);
            ossResult.setSuccess(true);
        } catch (OSSException ex) {
            log.warn("删除 OSS 对象失败: objectName={}, error={}", objectName, ex.getMessage());
            ossResult.setSuccess(false);
            ossResult.setMessage(ex.getMessage());
        }
        return ossResult;
    }

    @Override
    public OSSResult removeFiles(List<String> objectNameList) {
        OSSResult ossResult = new OSSResult();
        try {
            // 阿里云 OSS 批量删除每次最多 1000 个对象
            int batchSize = 1000;
            List<String> allFailedKeys = new java.util.ArrayList<>();

            for (int i = 0; i < objectNameList.size(); i += batchSize) {
                List<String> batch = objectNameList.subList(i, Math.min(i + batchSize, objectNameList.size()));
                DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(aliOSSConfiguration.getBucketName());
                deleteRequest.setKeys(batch);
                DeleteObjectsResult deleteResult = ossClient.deleteObjects(deleteRequest);
                // 返回删除失败的对象列表
                if (deleteResult.getDeletedObjects() != null && !deleteResult.getDeletedObjects().isEmpty()) {
                    allFailedKeys.addAll(deleteResult.getDeletedObjects());
                }
            }

            if (allFailedKeys.isEmpty()) {
                ossResult.setSuccess(true);
            } else {
                ossResult.setSuccess(false);
                ossResult.setMessage("部分对象删除失败: " + String.join(", ", allFailedKeys));
            }
        } catch (OSSException ex) {
            log.warn("批量删除 OSS 对象失败: error={}", ex.getMessage());
            ossResult.setSuccess(false);
            ossResult.setMessage(ex.getMessage());
        }
        return ossResult;
    }

    @Override
    public OSSResult getBucketStats() {
        OSSResult ossResult = new OSSResult();
        try {
            long totalStorage = 0;
            long fileCount = 0;
            Date lastModified = null;

            String nextContinuationToken = null;
            boolean isTruncated = true;
            while (isTruncated) {
                ListObjectsV2Request listRequest = new ListObjectsV2Request(aliOSSConfiguration.getBucketName());
                listRequest.setMaxKeys(1000);
                if (nextContinuationToken != null) {
                    listRequest.setContinuationToken(nextContinuationToken);
                }
                ListObjectsV2Result listResult = ossClient.listObjectsV2(listRequest);
                for (OSSObjectSummary summary : listResult.getObjectSummaries()) {
                    totalStorage += summary.getSize();
                    fileCount++;
                    if (lastModified == null || (summary.getLastModified() != null && summary.getLastModified().after(lastModified))) {
                        lastModified = summary.getLastModified();
                    }
                }
                isTruncated = listResult.isTruncated();
                nextContinuationToken = listResult.getNextContinuationToken();
            }

            OSSBucketStats stats = new OSSBucketStats()
                    .setBucketName(aliOSSConfiguration.getBucketName())
                    .setRegion(aliOSSConfiguration.getRegion())
                    .setTotalStorage(totalStorage)
                    .setFileCount(fileCount)
                    .setLastModified(lastModified);
            ossResult.setSuccess(true);
            ossResult.setBucketStats(stats);
        } catch (OSSException oe) {
            ossResult.setSuccess(false);
            ossResult.setMessage(oe.getMessage());
        }
        return ossResult;
    }

    @Override
    public boolean objectExists(String objectName) {
        try {
            return ossClient.doesObjectExist(aliOSSConfiguration.getBucketName(), objectName);
        } catch (OSSException e) {
            log.warn("检查 OSS 对象存在性失败: objectName={}, error={}", objectName, e.getMessage());
            return false;
        }
    }

}
