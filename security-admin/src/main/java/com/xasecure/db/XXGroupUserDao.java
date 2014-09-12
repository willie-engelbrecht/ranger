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

 package com.xasecure.db;


import java.util.ArrayList;
import java.util.List;

import javax.persistence.NoResultException;

import org.apache.log4j.Logger;

import com.xasecure.entity.XXGroupUser;
import com.xasecure.entity.XXPermMap;

import com.xasecure.common.*;
import com.xasecure.common.db.*;
import com.xasecure.entity.*;

public class XXGroupUserDao extends BaseDao<XXGroupUser> {
	static final Logger logger = Logger.getLogger(XXGroupUserDao.class);

	public XXGroupUserDao(XADaoManagerBase daoManager) {
		super(daoManager);
	}

	public void deleteByGroupIdAndUserId(Long groupId, Long userId) {
		getEntityManager()
				.createNamedQuery("XXGroupUser.deleteByGroupIdAndUserId")
				.setParameter("userId", userId)
				.setParameter("parentGroupId", groupId).executeUpdate();

	}

	public List<XXGroupUser> findByUserId(Long userId) {
		if (userId != null) {
			try {
				return getEntityManager()
						.createNamedQuery("XXGroupUser.findByUserId", XXGroupUser.class)
						.setParameter("userId", userId)
						.getResultList();
			} catch (NoResultException e) {
				logger.debug(e.getMessage());
			}
		} else {
			logger.debug("ResourceId not provided.");
			return new ArrayList<XXGroupUser>();
		}
		return null;
	}
}
