/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.biz;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.ranger.common.AppConstants;
import org.apache.ranger.common.ContextUtil;
import org.apache.ranger.common.GUIDUtil;
import org.apache.ranger.common.MessageEnums;
import org.apache.ranger.common.PropertiesUtil;
import org.apache.ranger.common.RESTErrorUtil;
import org.apache.ranger.common.RangerCommonEnums;
import org.apache.ranger.common.RangerConstants;
import org.apache.ranger.common.StringUtil;
import org.apache.ranger.common.UserSessionBase;
import org.apache.ranger.common.db.BaseDao;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.entity.XXAsset;
import org.apache.ranger.entity.XXDBBase;
import org.apache.ranger.entity.XXGroup;
import org.apache.ranger.entity.XXPermMap;
import org.apache.ranger.entity.XXPortalUser;
import org.apache.ranger.entity.XXResource;
import org.apache.ranger.entity.XXService;
import org.apache.ranger.entity.XXServiceDef;
import org.apache.ranger.entity.XXTrxLog;
import org.apache.ranger.entity.XXUser;
import org.apache.ranger.plugin.model.RangerBaseModelObject;
import org.apache.ranger.plugin.model.RangerService;
import org.apache.ranger.plugin.store.EmbeddedServiceDefsUtil;
import org.apache.ranger.service.AbstractBaseResourceService;
import org.apache.ranger.view.VXDataObject;
import org.apache.ranger.view.VXPortalUser;
import org.apache.ranger.view.VXResource;
import org.apache.ranger.view.VXResponse;
import org.apache.ranger.view.VXString;
import org.apache.ranger.view.VXStringList;
import org.apache.ranger.view.VXUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RangerBizUtil {
	private static final Logger logger = Logger.getLogger(RangerBizUtil.class);

	@Autowired
	RESTErrorUtil restErrorUtil;

	@Autowired
	RangerDaoManager daoManager;

	@Autowired
	StringUtil stringUtil;

	@Autowired
	UserMgr userMgr;

	@Autowired
	GUIDUtil guidUtil;
	
	Set<Class<?>> groupEditableClasses;
	private Class<?>[] groupEditableClassesList = {};

	Map<String, Integer> classTypeMappings = new HashMap<String, Integer>();
	private int maxFirstNameLength;
	int maxDisplayNameLength = 150;
	public final String EMPTY_CONTENT_DISPLAY_NAME = "...";
	boolean enableResourceAccessControl;
        private SecureRandom random;
	private static final String PATH_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrst0123456789-_.";
	private static char[] PATH_CHAR_SET = PATH_CHARS.toCharArray();
	private static int PATH_CHAR_SET_LEN = PATH_CHAR_SET.length;
	public static final String AUDIT_STORE_RDBMS = "DB";
	public static final String AUDIT_STORE_SOLR = "solr";

	String auditDBType = AUDIT_STORE_RDBMS;

	static String fileSeparator = PropertiesUtil.getProperty("ranger.file.separator", "/");

	public RangerBizUtil() {
		maxFirstNameLength = Integer.parseInt(PropertiesUtil.getProperty("ranger.user.firstname.maxlength", "16"));
		maxDisplayNameLength = PropertiesUtil.getIntProperty("ranger.bookmark.name.maxlen", maxDisplayNameLength);

		groupEditableClasses = new HashSet<Class<?>>(
				Arrays.asList(groupEditableClassesList));
		enableResourceAccessControl = PropertiesUtil.getBooleanProperty("ranger.resource.accessControl.enabled", true);

		auditDBType = PropertiesUtil.getProperty("ranger.audit.source.type",
				auditDBType).toLowerCase();
		logger.info("java.library.path is " + System.getProperty("java.library.path"));
		logger.info("Audit datasource is " + auditDBType);
                random = new SecureRandom();
	}

	public <T extends XXDBBase> List<? extends XXDBBase> getParentObjects(
			T object) {
		List<XXDBBase> parentObjectList = null;
		// if (checkParentAcess.contains(object.getMyClassType())) {
		// parentObjectList = new ArrayList<MBase>();
		// }
		return parentObjectList;
	}

	public int getClassType(Class<?> klass) {
		String className = klass.getName();
		// See if this mapping is already in the database
		Integer classType = classTypeMappings.get(className);
		if (classType == null) {
			// Instantiate the class and call the getClassType method
			if (XXDBBase.class.isAssignableFrom(klass)) {
				try {
					XXDBBase gjObj = (XXDBBase) klass.newInstance();
					classType = gjObj.getMyClassType();
					classTypeMappings.put(className, classType);
				} catch (Throwable ex) {
					logger.error("Error instantiating object for class "
							+ className, ex);
				}
			}
		}
		if (classType == null) {
			return RangerCommonEnums.CLASS_TYPE_NONE;
		} else {
			return classType;
		}
	}

	// Access control methods
	public void checkSystemAdminAccess() {
		UserSessionBase currentUserSession = ContextUtil
				.getCurrentUserSession();
		if (currentUserSession != null && currentUserSession.isUserAdmin()) {
			return;
		}
		throw restErrorUtil
				.create403RESTException("Only System Administrators can add accounts");
	}

	/**
	 * @param contentType
	 * @return
	 */
	public int getMimeTypeInt(String contentType) {
		if ("JPEG".equalsIgnoreCase(contentType)
				|| "JPG".equalsIgnoreCase(contentType)
				|| contentType.endsWith("jpg") || contentType.endsWith("jpeg")) {
			return RangerConstants.MIME_JPEG;
		}
		if ("PNG".equalsIgnoreCase(contentType) || contentType.endsWith("png")) {
			return RangerConstants.MIME_PNG;
		}
		return RangerConstants.MIME_UNKNOWN;
	}

	/**
	 * @param mimeType
	 * @return
	 */
	public String getMimeType(int mimeType) {
		switch (mimeType) {
		case RangerConstants.MIME_JPEG:
			return "jpg";
		case RangerConstants.MIME_PNG:
			return "png";
		}
		return "";
	}

	/**
	 * @param contentType
	 * @return
	 */
	public String getImageExtension(String contentType) {
		if (contentType.toLowerCase().endsWith("jpg")
				|| contentType.toLowerCase().endsWith("jpeg")) {
			return "jpg";
		} else if (contentType.toLowerCase().endsWith("png")) {
			return "png";
		}
		return "";
	}

	/**
	 * @param file
	 * @return
	 */
	public String getFileNameWithoutExtension(File file) {
		if (file != null) {
			String fileName = file.getName();
			if (fileName.indexOf(".") > 0) {
				return fileName.substring(0, fileName.indexOf("."));
			}
			return fileName;

		}
		return null;
	}

	public String getDisplayNameForClassName(XXDBBase obj) {
		String classTypeDisplayName = RangerConstants
				.getLabelFor_ClassTypes(obj.getMyClassType());
		if (classTypeDisplayName == null) {
			logger.error(
					"Error get name for class type. obj=" + obj.toString(),
					new Throwable());
		}
		return classTypeDisplayName;
	}

	public String getDisplayName(XXDBBase obj) {
		if (obj != null) {
			return handleGetDisplayName(obj.getMyDisplayValue());
		} else {
			return handleGetDisplayName(null);
		}
	}

	/**
	 * @param displayValue
	 * @return
	 */
	private String handleGetDisplayName(String displayValue) {
		if (displayValue == null || displayValue.trim().isEmpty()) {
			return EMPTY_CONTENT_DISPLAY_NAME;
		}

		if (displayValue.length() > maxDisplayNameLength) {
			displayValue = displayValue.substring(0, maxDisplayNameLength - 3)
					.concat("...");
		}
		return displayValue;
	}

	/**
	 * @param userProfile
	 * @return
	 */
	public String generatePublicName(VXPortalUser userProfile,
			XXPortalUser gjUser) {
		return generatePublicName(userProfile.getFirstName(),
				userProfile.getLastName());
	}

	public String generatePublicName(String firstName, String lastName) {
		String publicName = null;
		String fName = firstName;
		if (firstName.length() > maxFirstNameLength) {
			fName = firstName.substring(0, maxFirstNameLength - (1 + 3))
					+ "...";
		}
		if (lastName != null && lastName.length() > 0) {
			publicName = fName + " " + lastName.substring(0, 1) + ".";
		}
		return publicName;
	}

	public void updateCloneReferences(XXDBBase obj) {
		if (obj == null) {
			return;
		}
	}

	public Long getForUserId(XXDBBase resource) {
		return null;
	}

	public XXDBBase getMObject(int objClassType, Long objId) {
		XXDBBase obj = null;

		if (objId != null) {
			BaseDao<?> dao = daoManager.getDaoForClassType(objClassType);

			if (dao != null) {
				obj = (XXDBBase) dao.getById(objId);
			}
		}

		return obj;
	}

	public XXDBBase getMObject(VXDataObject vXDataObject) {
		if (vXDataObject != null) {
			return getMObject(vXDataObject.getMyClassType(),
					vXDataObject.getId());
		}
		return null;
	}

	public VXDataObject getVObject(int objClassType, Long objId) {
		if (objId == null) {
			return null;
		}
		if (objClassType == RangerConstants.CLASS_TYPE_USER_PROFILE) {
			return userMgr.mapXXPortalUserVXPortalUser(daoManager
					.getXXPortalUser().getById(objId));
		}
		try {
			AbstractBaseResourceService<?, ?> myService = AbstractBaseResourceService
					.getService(objClassType);
			if (myService != null) {
				return myService.readResource(objId);
			}
		} catch (Throwable t) {
			logger.error("Error reading resource. objectClassType="
					+ objClassType + ", objectId=" + objId, t);
		}
		return null;
	}

	public void deleteReferencedObjects(XXDBBase obj) {

		if (obj == null) {
			return;
		}
		if (obj.getMyClassType() == RangerConstants.CLASS_TYPE_NONE) {
			return;
		}

	}

	/**
	 * @param obj
	 */
	void deleteObjects(List<XXDBBase> objs) {

	}

	void deleteObject(XXDBBase obj) {
		AbstractBaseResourceService<?, ?> myService = AbstractBaseResourceService
				.getService(obj.getMyClassType());
		if (myService != null) {
			myService.deleteResource(obj.getId());
		} else {
			logger.error("Service not found for obj=" + obj, new Throwable());
		}
	}

	public <T extends XXDBBase> Class<? extends XXDBBase> getContextObject(
			int objectClassType, Long objectId) {
		return null;
	}

	public VXStringList mapStringListToVStringList(List<String> stringList) {
		if (stringList == null) {
			return null;
		}

		List<VXString> vStringList = new ArrayList<VXString>();
		for (String str : stringList) {
			VXString vXString = new VXString();
			vXString.setValue(str);
			vStringList.add(vXString);
		}

		return new VXStringList(vStringList);
	}

	/**
	 * return response object if users is having permission on given resource
	 *
	 * @param vXResource
	 * @param permission
	 * @return
	 */
	public VXResponse hasPermission(VXResource vXResource, int permission) {

		VXResponse vXResponse = new VXResponse();
		if (!enableResourceAccessControl) {
			logger.debug("Resource Access Control is disabled !!!");
			return vXResponse;
		}

		if (vXResource == null) {
			vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
			vXResponse.setMsgDesc("Please provide valid policy.");
			return vXResponse;
		}

		String resourceNames = vXResource.getName();
		if (stringUtil.isEmpty(resourceNames)) {
			vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
			vXResponse.setMsgDesc("Please provide valid policy.");
			return vXResponse;
		}

		if (isAdmin()) {
			return vXResponse;
		}

		Long xUserId = getXUserId();
		Long assetId = vXResource.getAssetId();
		List<XXResource> xResourceList = daoManager.getXXResource()
				.findByAssetIdAndResourceStatus(assetId,
						AppConstants.STATUS_ENABLED);

		XXAsset xAsset = daoManager.getXXAsset().getById(assetId);
		int assetType = xAsset.getAssetType();

		vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
		vXResponse.setMsgDesc("Permission Denied !");

		if (assetType == AppConstants.ASSET_HIVE) {
			String[] requestResNameList = resourceNames.split(",");
			if (stringUtil.isEmpty(vXResource.getUdfs())) {
				int reqTableType = vXResource.getTableType();
				int reqColumnType = vXResource.getColumnType();
				for (String resourceName : requestResNameList) {
					boolean matchFound = matchHivePolicy(resourceName,
							xResourceList, xUserId, permission, reqTableType,
							reqColumnType, false);
					if (!matchFound) {
						vXResponse
								.setMsgDesc("You're not permitted to perform "
										+ "the action for resource path : "
										+ resourceName);
						vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
						return vXResponse;
					}
				}
			} else {
				for (String resourceName : requestResNameList) {
					boolean matchFound = matchHivePolicy(resourceName,
							xResourceList, xUserId, permission);
					if (!matchFound) {
						vXResponse
								.setMsgDesc("You're not permitted to perform "
										+ "the action for resource path : "
										+ resourceName);
						vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
						return vXResponse;
					}
				}
			}
			vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
			return vXResponse;
		} else if (assetType == AppConstants.ASSET_HBASE) {
			String[] requestResNameList = resourceNames.split(",");
			for (String resourceName : requestResNameList) {
				boolean matchFound = matchHbasePolicy(resourceName,
						xResourceList, vXResponse, xUserId, permission);
				if (!matchFound) {
					vXResponse.setMsgDesc("You're not permitted to perform "
							+ "the action for resource path : " + resourceName);
					vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
					return vXResponse;
				}
			}
			vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
			return vXResponse;
		} else if (assetType == AppConstants.ASSET_HDFS) {
			String[] requestResNameList = resourceNames.split(",");
			for (String resourceName : requestResNameList) {
				boolean matchFound = matchHdfsPolicy(resourceName,
						xResourceList, xUserId, permission);
				if (!matchFound) {
					vXResponse.setMsgDesc("You're not permitted to perform "
							+ "the action for resource path : " + resourceName);
					vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
					return vXResponse;
				}
			}
			vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
			return vXResponse;
		} else if (assetType == AppConstants.ASSET_KNOX) {
			String[] requestResNameList = resourceNames.split(",");
			for (String resourceName : requestResNameList) {
				boolean matchFound = matchKnoxPolicy(resourceName,
						xResourceList, xUserId, permission);
				if (!matchFound) {
					vXResponse.setMsgDesc("You're not permitted to perform "
							+ "the action for resource path : " + resourceName);
					vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
					return vXResponse;
				}
			}
			vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
			return vXResponse;
		} else if (assetType == AppConstants.ASSET_STORM) {
			String[] requestResNameList = resourceNames.split(",");
			for (String resourceName : requestResNameList) {
				boolean matchFound = matchStormPolicy(resourceName,
						xResourceList, xUserId, permission);
				if (!matchFound) {
					vXResponse.setMsgDesc("You're not permitted to perform "
							+ "the action for resource path : " + resourceName);
					vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
					return vXResponse;
				}
			}
			vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
			return vXResponse;
		}
		return vXResponse;
	}

	/**
	 * return true id current logged in session is owned by admin
	 *
	 * @return
	 */
	public boolean isAdmin() {
		UserSessionBase currentUserSession = ContextUtil
				.getCurrentUserSession();
		if (currentUserSession == null) {
			logger.debug("Unable to find session.");
			return false;
		}

		if (currentUserSession.isUserAdmin()) {
			return true;
		}
		return false;
	}

	/**
	 * return username of currently logged in user
	 *
	 * @return
	 */
	public String getCurrentUserLoginId() {
		String ret = null;

		UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();

		if (currentUserSession != null) {
			ret = currentUserSession.getLoginId();
		}

		return ret;
	}

	/**
	 * returns current user's userID from active user sessions
	 *
	 * @return
	 */
	public Long getXUserId() {

		UserSessionBase currentUserSession = ContextUtil
				.getCurrentUserSession();
		if (currentUserSession == null) {
			logger.debug("Unable to find session.");
			return null;
		}

		XXPortalUser user = daoManager.getXXPortalUser().getById(
				currentUserSession.getUserId());
		if (user == null) {
			logger.debug("XXPortalUser not found with logged in user id : "
					+ currentUserSession.getUserId());
			return null;
		}

		XXUser xUser = daoManager.getXXUser().findByUserName(user.getLoginId());
		if (xUser == null) {
			logger.debug("XXPortalUser not found for user id :" + user.getId()
					+ " with name " + user.getFirstName());
			return null;
		}

		return xUser.getId();
	}

	/**
	 * returns true if user is having required permission on given Hdfs resource
	 *
	 * @param resourceName
	 * @param xResourceList
	 * @param xUserId
	 * @param permission
	 * @return
	 */
	private boolean matchHdfsPolicy(String resourceName,
			List<XXResource> xResourceList, Long xUserId, int permission) {
		boolean matchFound = false;
		resourceName = replaceMetaChars(resourceName);

		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}
			Long resourceId = xResource.getId();
			matchFound = checkUsrPermForPolicy(xUserId, permission, resourceId);
			if (matchFound) {
				matchFound = false;
				String resource = xResource.getName();
				String[] dbResourceNameList = resource.split(",");
				for (String dbResourceName : dbResourceNameList) {
					if (comparePathsForExactMatch(resourceName, dbResourceName)) {
						matchFound = true;
					} else {
						if (xResource.getIsRecursive() == AppConstants.BOOL_TRUE) {
							matchFound = isRecursiveWildCardMatch(resourceName,
									dbResourceName);
						} else {
							matchFound = nonRecursiveWildCardMatch(
									resourceName, dbResourceName);
						}
					}
					if (matchFound) {
						break;
					}
				}
				if (matchFound) {
					break;
				}
			}
		}
		return matchFound;
	}

	/**
	 * returns true if user is having required permission on given Hbase
	 * resource
	 *
	 * @param resourceName
	 * @param xResourceList
	 * @param vXResponse
	 * @param xUserId
	 * @param permission
	 * @return
	 */
	public boolean matchHbasePolicy(String resourceName,
			List<XXResource> xResourceList, VXResponse vXResponse,
			Long xUserId, int permission) {
		if (stringUtil.isEmpty(resourceName) || xResourceList == null
				|| xUserId == null) {
			return false;
		}

		String[] splittedResources = stringUtil.split(resourceName,
				fileSeparator);
		if (splittedResources.length < 1 || splittedResources.length > 3) {
			logger.debug("Invalid resourceName name : " + resourceName);
			return false;
		}

		String tblName = splittedResources.length > 0 ? splittedResources[0]
				: StringUtil.WILDCARD_ASTERISK;
		String colFamName = splittedResources.length > 1 ? splittedResources[1]
				: StringUtil.WILDCARD_ASTERISK;
		String colName = splittedResources.length > 2 ? splittedResources[2]
				: StringUtil.WILDCARD_ASTERISK;

		boolean policyMatched = false;
		// check all resources whether Hbase policy is enabled in any resource
		// of provided resource list
		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}
			Long resourceId = xResource.getId();
			boolean hasPermission = checkUsrPermForPolicy(xUserId, permission,
					resourceId);
			// if permission is enabled then load Tables,column family and
			// columns list from resource
			if (!hasPermission) {
				continue;
			}

			// 1. does the policy match the table?
			String[] xTables = stringUtil.isEmpty(xResource.getTables()) ? null
					: stringUtil.split(xResource.getTables(), ",");

			boolean matchFound = (xTables == null || xTables.length == 0) || matchPath(tblName, xTables);

			if (matchFound) {
				// 2. does the policy match the column?
				String[] xColumnFamilies = stringUtil.isEmpty(xResource
						.getColumnFamilies()) ? null : stringUtil.split(
						xResource.getColumnFamilies(), ",");

				matchFound = (xColumnFamilies == null || xColumnFamilies.length == 0)
						|| matchPath(colFamName, xColumnFamilies);

				if (matchFound) {
					// 3. does the policy match the columnFamily?
					String[] xColumns = stringUtil.isEmpty(xResource
							.getColumns()) ? null : stringUtil.split(
							xResource.getColumns(), ",");

					matchFound = (xColumns == null || xColumns.length == 0)
							|| matchPath(colName, xColumns);
				}
			}

			if (matchFound) {
				policyMatched = true;
				break;
			}
		}
		return policyMatched;
	}

	public boolean matchHivePolicy(String resourceName,
			List<XXResource> xResourceList, Long xUserId, int permission) {
		return matchHivePolicy(resourceName, xResourceList, xUserId,
				permission, 0, 0, true);
	}

	/**
	 * returns true if user is having required permission on given Hive resource
	 *
	 * @param resourceName
	 * @param xResourceList
	 * @param xUserId
	 * @param permission
	 * @param reqTableType
	 * @param reqColumnType
	 * @param isUdfPolicy
	 * @return
	 */
	public boolean matchHivePolicy(String resourceName,
			List<XXResource> xResourceList, Long xUserId, int permission,
			int reqTableType, int reqColumnType, boolean isUdfPolicy) {

		if (stringUtil.isEmpty(resourceName) || xResourceList == null
				|| xUserId == null) {
			return false;
		}

		String[] splittedResources = stringUtil.split(resourceName,
				fileSeparator);// get list of resources
		if (splittedResources.length < 1 || splittedResources.length > 3) {
			logger.debug("Invalid resource name : " + resourceName);
			return false;
		}

		String dbName = splittedResources.length > 0 ? splittedResources[0]
				: StringUtil.WILDCARD_ASTERISK;
		String tblName = splittedResources.length > 1 ? splittedResources[1]
				: StringUtil.WILDCARD_ASTERISK;
		String colName = splittedResources.length > 2 ? splittedResources[2]
				: StringUtil.WILDCARD_ASTERISK;

		boolean policyMatched = false;
		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}

			Long resourceId = xResource.getId();
			boolean hasPermission = checkUsrPermForPolicy(xUserId, permission,
					resourceId);

			if (!hasPermission) {
				continue;
			}

			// 1. does the policy match the database?
			String[] xDatabases = stringUtil.isEmpty(xResource.getDatabases()) ? null
					: stringUtil.split(xResource.getDatabases(), ",");

			boolean matchFound = (xDatabases == null || xDatabases.length == 0)
					|| matchPath(dbName, xDatabases);

			if (!matchFound) {
				continue;
			}

			// Type(either UDFs policy or non-UDFs policy) of current policy
			// should be of same as type of policy being iterated
			if (!stringUtil.isEmpty(xResource.getUdfs()) && !isUdfPolicy) {
				continue;
			}

			if (isUdfPolicy) {
				// 2. does the policy match the UDF?
				String[] xUdfs = stringUtil.isEmpty(xResource.getUdfs()) ? null
						: stringUtil.split(xResource.getUdfs(), ",");

				if (!matchPath(tblName, xUdfs)) {
					continue;
				} else {
					policyMatched = true;
					break;
				}
			} else {
				// 2. does the policy match the table?
				String[] xTables = stringUtil.isEmpty(xResource.getTables()) ? null
						: stringUtil.split(xResource.getTables(), ",");

				matchFound = (xTables == null || xTables.length == 0)
						|| matchPath(tblName, xTables);

				if (xResource.getTableType() == AppConstants.POLICY_EXCLUSION) {
					matchFound = !matchFound;
				}

				if (!matchFound) {
					continue;
				}

				// 3. does current policy match the column?
				String[] xColumns = stringUtil.isEmpty(xResource.getColumns()) ? null
						: stringUtil.split(xResource.getColumns(), ",");

				matchFound = (xColumns == null || xColumns.length == 0)
						|| matchPath(colName, xColumns);

				if (xResource.getColumnType() == AppConstants.POLICY_EXCLUSION) {
					matchFound = !matchFound;
				}

				if (!matchFound) {
					continue;
				} else {
					policyMatched = true;
					break;
				}
			}
		}
		return policyMatched;
	}

	/**
	 * returns true if user is having required permission on given Hbase
	 * resource
	 *
	 * @param resourceName
	 * @param xResourceList
	 * @param xUserId
	 * @param permission
	 * @return
	 */
	private boolean matchKnoxPolicy(String resourceName,
			List<XXResource> xResourceList,
			Long xUserId, int permission) {

		String[] splittedResources = stringUtil.split(resourceName,
				fileSeparator);
		int numberOfResources = splittedResources.length;
		if (numberOfResources < 1 || numberOfResources > 3) {
			logger.debug("Invalid policy name : " + resourceName);
			return false;
		}

		boolean policyMatched = false;
		// check all resources whether Knox policy is enabled in any resource
		// of provided resource list
		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}
			Long resourceId = xResource.getId();
			boolean hasPermission = checkUsrPermForPolicy(xUserId, permission,
					resourceId);
			// if permission is enabled then load Topologies,services list from
			// resource
			if (hasPermission) {
				String[] xTopologies = (xResource.getTopologies() == null || "".equalsIgnoreCase(xResource
						.getTopologies())) ? null
						: stringUtil.split(xResource.getTopologies(), ",");
				String[] xServices = (xResource.getServices() == null || "".equalsIgnoreCase(xResource
						.getServices())) ? null
						: stringUtil.split(xResource.getServices(), ",");

				boolean matchFound = false;

				for (int index = 0; index < numberOfResources; index++) {
					matchFound = false;
					// check whether given table resource matches with any
					// existing topology resource
					if (index == 0) {
						if (xTopologies != null) {
							for (String xTopology : xTopologies) {
								if (matchPath(splittedResources[index],
										xTopology)) {
									matchFound = true;
									continue;
								}
							}
						}
						if (!matchFound) {
							break;
						}
					} // check whether given service resource matches with
						// any existing service resource
					else if (index == 1) {
						if (xServices != null) {
							for (String xService : xServices) {
								if (matchPath(splittedResources[index],
										xService)) {
									matchFound = true;
									continue;
								}
							}
						}
						if (!matchFound) {
							break;
						}
					}
				}
				if (matchFound) {
					policyMatched = true;
					break;
				}
			}
		}
		return policyMatched;
	}

	/**
	 * returns true if user is having required permission on given STORM
	 * resource
	 *
	 * @param resourceName
	 * @param xResourceList
	 * @param xUserId
	 * @param permission
	 * @return
	 */
	private boolean matchStormPolicy(String resourceName,
			List<XXResource> xResourceList,
			Long xUserId, int permission) {

		String[] splittedResources = stringUtil.split(resourceName,
				fileSeparator);
		int numberOfResources = splittedResources.length;
		if (numberOfResources < 1 || numberOfResources > 3) {
			logger.debug("Invalid policy name : " + resourceName);
			return false;
		}

		boolean policyMatched = false;
		// check all resources whether Knox policy is enabled in any resource
		// of provided resource list
		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}
			Long resourceId = xResource.getId();
			boolean hasPermission = checkUsrPermForPolicy(xUserId, permission,
					resourceId);
			// if permission is enabled then load Topologies,services list from
			// resource
			if (hasPermission) {
				String[] xTopologies = (xResource.getTopologies() == null || "".equalsIgnoreCase(xResource
						.getTopologies())) ? null
						: stringUtil.split(xResource.getTopologies(), ",");
				/*
				 * String[] xServices = (xResource.getServices() == null ||
				 * xResource .getServices().equalsIgnoreCase("")) ? null :
				 * stringUtil.split(xResource.getServices(), ",");
				 */

				boolean matchFound = false;

				for (int index = 0; index < numberOfResources; index++) {
					matchFound = false;
					// check whether given table resource matches with any
					// existing topology resource
					if (index == 0) {
						if (xTopologies != null) {
							for (String xTopology : xTopologies) {
								if (matchPath(splittedResources[index],
										xTopology)) {
									matchFound = true;
									continue;
								}
							}
						}
					} // check whether given service resource matches with
						// any existing service resource
					/*
					 * else if (index == 1) { if(xServices!=null){ for (String
					 * xService : xServices) { if
					 * (matchPath(splittedResources[index], xService)) {
					 * matchFound = true; continue; } } } }
					 */
				}
				if (matchFound) {
					policyMatched = true;
					break;
				}
			}
		}
		return policyMatched;
	}

	/**
	 * returns path without meta characters
	 *
	 * @param path
	 * @return
	 */
	public String replaceMetaChars(String path) {
		if (path == null || path.isEmpty()) {
			return path;
		}

		if (path.contains("*")) {
			String replacement = getRandomString(5, 60);
			path = path.replaceAll("\\*", replacement);
		}
		if (path.contains("?")) {
			String replacement = getRandomString(1, 1);
			path = path.replaceAll("\\?", replacement);
		}
		return path;
	}

	/**
	 * returns random String of given length range
	 *
	 * @param minLen
	 * @param maxLen
	 * @return
	 */
	private String getRandomString(int minLen, int maxLen) {
		StringBuilder sb = new StringBuilder();
		int len = getRandomInt(minLen, maxLen);
		for (int i = 0; i < len; i++) {
			int charIdx = random.nextInt(PATH_CHAR_SET_LEN);
			sb.append(PATH_CHAR_SET[charIdx]);
		}
		return sb.toString();
	}

	/**
	 * return random integer number for given range
	 *
	 * @param min
	 * @param max
	 * @return
	 */
	private int getRandomInt(int min, int max) {
		if (min == max) {
			return min;
		} else {
			int interval = max - min;
			int randomNum = random.nextInt();
			if(randomNum<0){
				randomNum=Math.abs(randomNum);
			}
			return ((randomNum % interval) + min);
		}
	}

	/**
	 * returns true if given userID is having specified permission on specified
	 * resource
	 *
	 * @param xUserId
	 * @param permission
	 * @param resourceId
	 * @return
	 */
	private boolean checkUsrPermForPolicy(Long xUserId, int permission,
			Long resourceId) {
		// this snippet load user groups and permission map list from DB
		List<XXGroup> userGroups = new ArrayList<XXGroup>();
		List<XXPermMap> permMapList = new ArrayList<XXPermMap>();
		userGroups = daoManager.getXXGroup().findByUserId(xUserId);
		permMapList = daoManager.getXXPermMap().findByResourceId(resourceId);
		Long publicGroupId = getPublicGroupId();
		boolean matchFound = false;
		for (XXPermMap permMap : permMapList) {
			if (permMap.getPermType() == permission) {
				if (permMap.getPermFor() == AppConstants.XA_PERM_FOR_GROUP) {
					// check whether permission is enabled for public group or a
					// group to which user belongs
					matchFound = (publicGroupId != null && publicGroupId == permMap
							.getGroupId())
							|| isGroupInList(permMap.getGroupId(), userGroups);
				} else if (permMap.getPermFor() == AppConstants.XA_PERM_FOR_USER) {
					// check whether permission is enabled to user
					matchFound = permMap.getUserId().equals(xUserId);
				}
			}
			if (matchFound) {
				break;
			}
		}
		return matchFound;
	}

	public Long getPublicGroupId() {
		XXGroup xXGroupPublic = daoManager.getXXGroup().findByGroupName(
				RangerConstants.GROUP_PUBLIC);

		return xXGroupPublic != null ? xXGroupPublic.getId() : null;
	}

	/**
	 * returns true is given group id is in given group list
	 *
	 * @param groupId
	 * @param xGroupList
	 * @return
	 */
	public boolean isGroupInList(Long groupId, List<XXGroup> xGroupList) {
		for (XXGroup xGroup : xGroupList) {
			if (xGroup.getId().equals(groupId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * returns true if given path matches in same level or sub directories with
	 * given wild card pattern
	 *
	 * @param pathToCheck
	 * @param wildcardPath
	 * @return
	 */
	public boolean isRecursiveWildCardMatch(String pathToCheck,
			String wildcardPath) {
		if (pathToCheck != null) {
			if (wildcardPath != null && wildcardPath.equals(fileSeparator)) {
				return true;
			}
			StringBuilder sb = new StringBuilder();
			for (String p : pathToCheck.split(fileSeparator)) {
				sb.append(p);
				boolean matchFound = FilenameUtils.wildcardMatch(sb.toString(),
						wildcardPath);
				if (matchFound) {
					return true;
				}
				sb.append(fileSeparator);
			}
			sb = null;
		}
		return false;
	}

	/**
	 * return List<Integer>
	 *
	 * List of all possible parent return type for some specific resourceType
	 *
	 * @param resourceType
	 *            , assetType
	 *
	 */
	public List<Integer> getResorceTypeParentHirearchy(int resourceType,
			int assetType) {
		List<Integer> resourceTypeList = new ArrayList<Integer>();

		if (assetType == AppConstants.ASSET_HDFS) {
			resourceTypeList.add(AppConstants.RESOURCE_PATH);
		} else if (assetType == AppConstants.ASSET_HIVE) {
			resourceTypeList.add(AppConstants.RESOURCE_DB);
			if (resourceType == AppConstants.RESOURCE_TABLE) {
				resourceTypeList.add(AppConstants.RESOURCE_TABLE);
			} else if (resourceType == AppConstants.RESOURCE_UDF) {
				resourceTypeList.add(AppConstants.RESOURCE_UDF);
			} else if (resourceType == AppConstants.RESOURCE_COLUMN) {
				resourceTypeList.add(AppConstants.RESOURCE_TABLE);
				resourceTypeList.add(AppConstants.RESOURCE_COLUMN);
			}
		} else if (assetType == AppConstants.ASSET_HBASE) {
			resourceTypeList.add(AppConstants.RESOURCE_TABLE);
			if (resourceType == AppConstants.RESOURCE_COL_FAM) {
				resourceTypeList.add(AppConstants.RESOURCE_COL_FAM);
			} else if (resourceType == AppConstants.RESOURCE_COLUMN) {
				resourceTypeList.add(AppConstants.RESOURCE_COL_FAM);
				resourceTypeList.add(AppConstants.RESOURCE_COLUMN);
			}
		}

		return resourceTypeList;
	}

	/**
	 * return true if both path matches exactly, wild card matching is not
	 * checked
	 *
	 * @param path1
	 * @param path2
	 * @return
	 */
	public boolean comparePathsForExactMatch(String path1, String path2) {
		String pathSeparator = fileSeparator;
		if (!path1.endsWith(pathSeparator)) {
			path1 = path1.concat(pathSeparator);
		}
		if (!path2.endsWith(pathSeparator)) {
			path2 = path2.concat(pathSeparator);
		}
		return path1.equalsIgnoreCase(path2);
	}

	/**
	 * return true if both path matches at same level path, this function does
	 * not match sub directories
	 *
	 * @param pathToCheck
	 * @param wildcardPath
	 * @return
	 */
	public boolean nonRecursiveWildCardMatch(String pathToCheck,
			String wildcardPath) {
		if (pathToCheck != null && wildcardPath != null) {

			List<String> pathToCheckArray = new ArrayList<String>();
			List<String> wildcardPathArray = new ArrayList<String>();

			Collections.addAll(pathToCheckArray, pathToCheck.split(fileSeparator));
			Collections.addAll(wildcardPathArray, wildcardPath.split(fileSeparator));

			if (pathToCheckArray.size() == wildcardPathArray.size()) {
				boolean match = false;
				for (int index = 0; index < pathToCheckArray.size(); index++) {
					match = matchPath(pathToCheckArray.get(index),
							wildcardPathArray.get(index));
					if (!match)
						return match;
				}
				return match;
			}
		}
		return false;
	}

	/**
	 * returns true if first and second path are same
	 *
	 * @param pathToCheckFragment
	 * @param wildCardPathFragment
	 * @return
	 */
	private boolean matchPath(String pathToCheckFragment,
			String wildCardPathFragment) {
		if (pathToCheckFragment == null || wildCardPathFragment == null) {
			return false;
		}

		if (pathToCheckFragment.contains("*")
				|| pathToCheckFragment.contains("?")) {
			pathToCheckFragment = replaceMetaChars(pathToCheckFragment);

			if (wildCardPathFragment.contains("*")
					|| wildCardPathFragment.contains("?")) {
				return FilenameUtils.wildcardMatch(pathToCheckFragment,
						wildCardPathFragment, IOCase.SENSITIVE);
			} else {
				return false;
			}
		} else {
			if (wildCardPathFragment.contains("*")
					|| wildCardPathFragment.contains("?")) {
				return FilenameUtils.wildcardMatch(pathToCheckFragment,
						wildCardPathFragment, IOCase.SENSITIVE);
			} else {
				return pathToCheckFragment.trim().equals(
						wildCardPathFragment.trim());
			}
		}
	}

	private boolean matchPath(String pathToCheck, String[] wildCardPaths) {
		if (pathToCheck != null && wildCardPaths != null) {
			for (String wildCardPath : wildCardPaths) {
				if (matchPath(pathToCheck, wildCardPath)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * This method returns true if first parameter value is equal to others
	 * argument value passed
	 *
	 * @param checkValue
	 * @param otherValues
	 * @return
	 */
	public static boolean areAllEqual(int checkValue, int... otherValues) {
		for (int value : otherValues) {
			if (value != checkValue) {
				return false;
			}
		}
		return true;
	}

	public void createTrxLog(List<XXTrxLog> trxLogList) {
		if (trxLogList == null) {
			return;
		}

		UserSessionBase usb = ContextUtil.getCurrentUserSession();
		Long authSessionId = null;
		if (usb != null) {
			authSessionId = ContextUtil.getCurrentUserSession().getSessionId();
		}
		if(guidUtil != null){
		Long trxId = guidUtil.genLong();
		for (XXTrxLog xTrxLog : trxLogList) {
			if (xTrxLog != null) {
				if ("Password".equalsIgnoreCase(StringUtil.trim(xTrxLog.getAttributeName()))) {
					if (xTrxLog.getPreviousValue() != null
							&& !xTrxLog.getPreviousValue().trim().isEmpty()
							&& !"null".equalsIgnoreCase(xTrxLog
									.getPreviousValue().trim())) {
						xTrxLog.setPreviousValue(AppConstants.Masked_String);
					}
					if (xTrxLog.getNewValue() != null
							&& !xTrxLog.getNewValue().trim().isEmpty()
							&& !"null".equalsIgnoreCase(xTrxLog.getNewValue()
									.trim())) {
						xTrxLog.setNewValue(AppConstants.Masked_String);
					}
				}
				xTrxLog.setTransactionId(trxId.toString());
				if (authSessionId != null) {
					xTrxLog.setSessionId("" + authSessionId);
				}
				xTrxLog.setSessionType("Spring Authenticated Session");
				xTrxLog.setRequestId(trxId.toString());
				daoManager.getXXTrxLog().create(xTrxLog);
			}
		}
		}
	}

	public static int getDBFlavor() {
		String[] propertyNames = { "xa.db.flavor",
									"ranger.jpa.jdbc.dialect",
									"ranger.jpa.jdbc.url",
									"ranger.jpa.jdbc.driver"
								};

		for(String propertyName : propertyNames) {
			String propertyValue = PropertiesUtil.getProperty(propertyName);

			if(StringUtils.isBlank(propertyValue)) {
				continue;
			}

			if (StringUtils.containsIgnoreCase(propertyValue, "mysql")) {
				return AppConstants.DB_FLAVOR_MYSQL;
			} else if (StringUtils.containsIgnoreCase(propertyValue, "oracle")) {
				return AppConstants.DB_FLAVOR_ORACLE;
			} else if (StringUtils.containsIgnoreCase(propertyValue, "postgresql")) {
				return AppConstants.DB_FLAVOR_POSTGRES;
			} else if (StringUtils.containsIgnoreCase(propertyValue, "sqlserver")) {
				return AppConstants.DB_FLAVOR_SQLSERVER;
			} else if (StringUtils.containsIgnoreCase(propertyValue, "mssql")) {
				return AppConstants.DB_FLAVOR_SQLSERVER;
			} else if (StringUtils.containsIgnoreCase(propertyValue, "sqlanywhere")) {
				return AppConstants.DB_FLAVOR_SQLANYWHERE;
			} else if (StringUtils.containsIgnoreCase(propertyValue, "sqla")) {
				return AppConstants.DB_FLAVOR_SQLANYWHERE;
			}else {
				if(logger.isDebugEnabled()) {
					logger.debug("DB Flavor could not be determined from property - " + propertyName + "=" + propertyValue);
				}
			}
		}

		logger.error("DB Flavor could not be determined");

		return AppConstants.DB_FLAVOR_UNKNOWN;
	}

	public String getDBVersion(){
		return daoManager.getXXUser().getDBVersion();
    }

	public String getAuditDBType() {
		return auditDBType;
	}

	public void setAuditDBType(String auditDBType) {
		this.auditDBType = auditDBType;
	}

	/**
	 * return true id current logged in session is owned by keyadmin
	 *
	 * @return
	 */
	public boolean isKeyAdmin() {
		UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();
		if (currentUserSession == null) {
			logger.debug("Unable to find session.");
			return false;
		}

		if (currentUserSession.isKeyAdmin()) {
			return true;
		}
		return false;
	}

	/**
	 * @param xxDbBase
	 * @param baseModel
	 * @return Boolean
	 *
	 * @NOTE: Kindly check all the references of this function before making any changes
	 */
	public Boolean hasAccess(XXDBBase xxDbBase, RangerBaseModelObject baseModel) {
		UserSessionBase session = ContextUtil.getCurrentUserSession();
		if (session == null) {
			logger.info("User session not found, granting access.");
			return true;
		}

		boolean isKeyAdmin = session.isKeyAdmin();
		boolean isSysAdmin = session.isUserAdmin();
		boolean isUser = false;

		List<String> roleList = session.getUserRoleList();
		if (roleList.contains(RangerConstants.ROLE_USER)) {
			isUser = true;
		}

		if (xxDbBase != null && xxDbBase instanceof XXServiceDef) {
			XXServiceDef xServiceDef = (XXServiceDef) xxDbBase;
			String implClass = xServiceDef.getImplclassname();
			if (implClass == null) {
				return false;
			}

			if (isKeyAdmin && EmbeddedServiceDefsUtil.KMS_IMPL_CLASS_NAME.equals(implClass)) {
				return true;
			} else if ((isSysAdmin || isUser) && !EmbeddedServiceDefsUtil.KMS_IMPL_CLASS_NAME.equals(implClass)) {
				return true;
			}
		}

		if (xxDbBase != null && xxDbBase instanceof XXService) {

			// TODO: As of now we are allowing SYS_ADMIN to create/update/read/delete all the
			// services including KMS
			if (isSysAdmin) {
				return true;
			}

			XXService xService = (XXService) xxDbBase;
			XXServiceDef xServiceDef = daoManager.getXXServiceDef().getById(xService.getType());
			String implClass = xServiceDef.getImplclassname();
			if (implClass == null) {
				return false;
			}

			if (isKeyAdmin && EmbeddedServiceDefsUtil.KMS_IMPL_CLASS_NAME.equals(implClass)) {
				return true;
			} else if (isUser && !EmbeddedServiceDefsUtil.KMS_IMPL_CLASS_NAME.equals(implClass)) {
				return true;
			}
			// else if ((isSysAdmin || isUser) && !implClass.equals(EmbeddedServiceDefsUtil.KMS_IMPL_CLASS_NAME)) {
			// return true;
			// }
		}
		return false;
	}

	public void hasAdminPermissions(String objType) {

		UserSessionBase session = ContextUtil.getCurrentUserSession();

		if (session == null) {
			throw restErrorUtil.createRESTException("UserSession cannot be null, only Admin can create/update/delete "
					+ objType, MessageEnums.OPER_NO_PERMISSION);
		}

		if (!session.isKeyAdmin() && !session.isUserAdmin()) {
			throw restErrorUtil.createRESTException(
					"User is not allowed to update service-def, only Admin can create/update/delete " + objType,
					MessageEnums.OPER_NO_PERMISSION);
		}
	}

	public void hasKMSPermissions(String objType, String implClassName) {
		UserSessionBase session = ContextUtil.getCurrentUserSession();

		if (session.isKeyAdmin() && !EmbeddedServiceDefsUtil.KMS_IMPL_CLASS_NAME.equals(implClassName)) {
			throw restErrorUtil.createRESTException("KeyAdmin can create/update/delete only KMS " + objType,
					MessageEnums.OPER_NO_PERMISSION);
		}

		// TODO: As of now we are allowing SYS_ADMIN to create/update/read/delete all the
		// services including KMS

		if ("Service-Def".equalsIgnoreCase(objType) && session.isUserAdmin() && EmbeddedServiceDefsUtil.KMS_IMPL_CLASS_NAME.equals(implClassName)) {
			throw restErrorUtil.createRESTException("System Admin cannot create/update/delete KMS " + objType,
					MessageEnums.OPER_NO_PERMISSION);
		}
	}

	public boolean checkUserAccessible(VXUser vXUser) {
		if(isKeyAdmin() && vXUser.getUserRoleList().contains(RangerConstants.ROLE_SYS_ADMIN)) {
			throw restErrorUtil.createRESTException("Logged in user is not allowd to create/update user",
					MessageEnums.OPER_NO_PERMISSION);
		}

		if(isAdmin() && vXUser.getUserRoleList().contains(RangerConstants.ROLE_KEY_ADMIN)) {
			throw restErrorUtil.createRESTException("Logged in user is not allowd to create/update user",
					MessageEnums.OPER_NO_PERMISSION);
		}

		return true;
	}
	
	public boolean isSSOEnabled() {
		UserSessionBase session = ContextUtil.getCurrentUserSession();
		if (session != null) {
			return session.isSSOEnabled() == null ? PropertiesUtil.getBooleanProperty("ranger.sso.enabled", false) : session.isSSOEnabled();
		} else {
			throw restErrorUtil.createRESTException(
					"User session is not created",
					MessageEnums.OPER_NOT_ALLOWED_FOR_STATE);
		}
	}
	
	public boolean isUserAllowed(RangerService rangerService, String cfgNameAllowedUsers) {
		Map<String, String> map = rangerService.getConfigs();
		String user = null;
		UserSessionBase userSession = ContextUtil.getCurrentUserSession();
		if(userSession != null){
			user = userSession.getLoginId();
		}
		if (map != null && map.containsKey(cfgNameAllowedUsers)) {
			String userNames = map.get(cfgNameAllowedUsers);
			String[] userList = userNames.split(",");
			if(userList != null){
				for (String u : userList) {
					if ("*".equals(u) || (user != null && u.equalsIgnoreCase(user))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean isUserAllowedForGrantRevoke(RangerService rangerService,
			String cfgNameAllowedUsers, String userName) {
		Map<String, String> map = rangerService.getConfigs();

		if (map != null && map.containsKey(cfgNameAllowedUsers)) {
			String userNames = map.get(cfgNameAllowedUsers);
			String[] userList = userNames.split(",");
			if (userList != null) {
				for (String u : userList) {
					if ("*".equals(u) || (userName != null && u.equalsIgnoreCase(userName))) {
						return true;
					}
				}
			}
		}
		return false;
	}	

}
