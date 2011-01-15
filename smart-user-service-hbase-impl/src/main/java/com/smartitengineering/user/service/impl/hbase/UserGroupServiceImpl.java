/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.smartitengineering.user.service.impl.hbase;

import com.google.inject.Inject;
import com.smartitengineering.dao.common.CommonReadDao;
import com.smartitengineering.dao.common.CommonWriteDao;
import com.smartitengineering.dao.impl.hbase.spi.RowCellIncrementor;
import com.smartitengineering.user.domain.UniqueConstrainedField;
import com.smartitengineering.user.domain.User;
import com.smartitengineering.user.domain.UserGroup;
import com.smartitengineering.user.observer.CRUDObservable;
import com.smartitengineering.user.observer.ObserverNotification;
import com.smartitengineering.user.service.ExceptionMessage;
import com.smartitengineering.user.service.UserGroupService;
import com.smartitengineering.user.service.impl.hbase.domain.AutoId;
import com.smartitengineering.user.service.impl.hbase.domain.KeyableObject;
import com.smartitengineering.user.service.impl.hbase.domain.UniqueKey;
import com.smartitengineering.user.service.impl.hbase.domain.UniqueKeyIndex;
import java.util.Collection;
import java.util.Date;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author imyousuf
 */
public class UserGroupServiceImpl implements UserGroupService {

  @Inject
  private CommonWriteDao<UserGroup> writeDao;
  @Inject
  private CommonReadDao<UserGroup, Long> readDao;
  @Inject
  private CommonWriteDao<UniqueKeyIndex> uniqueKeyIndexWriteDao;
  @Inject
  private CommonReadDao<UniqueKeyIndex, UniqueKey> uniqueKeyIndexReadDao;
  @Inject
  private CommonWriteDao<AutoId> autoIdWriteDao;
  @Inject
  private CommonReadDao<AutoId, String> autoIdReadDao;
  @Inject
  private CRUDObservable observable;
  @Inject
  private RowCellIncrementor<UserGroup, AutoId, String> idIncrementor;
  private boolean autoIdInitialized = false;
  protected transient Logger logger = LoggerFactory.getLogger(getClass());

  protected boolean checkAndInitializeAutoId(String autoId) throws RuntimeException {
    AutoId id = autoIdReadDao.getById(autoId);
    if (id == null) {
      id = new AutoId();
      id.setValue(Long.MAX_VALUE);
      id.setId(autoId);
      try {
        autoIdWriteDao.save(id);
        return true;
      }
      catch (RuntimeException ex) {
        logger.error("Could not initialize usergroup auto id!", ex);
        throw ex;
      }
    }
    else {
      return true;
    }
  }

  protected void checkAndInitializeAutoId() {
    if (!autoIdInitialized) {
      autoIdInitialized = checkAndInitializeAutoId(KeyableObject.USER_GROUP.name());
    }
  }

  protected UniqueKey getUniqueKeyOfIndexForUserGroup(UserGroup userGroup) {
    final String name = userGroup.getName();
    return getUniqueKeyOfIndexForUserGroupName(name, userGroup.getOrganization().getUniqueShortName());
  }

  protected UniqueKey getUniqueKeyOfIndexForUserGroupName(final String name, final String orgShortName) {
    UniqueKey key = new UniqueKey();
    key.setKey(name);
    key.setObject(KeyableObject.USER_GROUP);
    key.setOrgId(orgShortName);
    return key;
  }

  @Override
  public void save(UserGroup userGroup) {
    checkAndInitializeAutoId();
    validateUserGroup(userGroup);
    final Date date = new Date();
    userGroup.setCreationDate(date);
    userGroup.setLastModifiedDate(date);
    try {
      long nextId = idIncrementor.incrementAndGet(KeyableObject.USER_GROUP.name(), -1l);
      UniqueKey key = getUniqueKeyOfIndexForUserGroup(userGroup);
      UniqueKeyIndex index = new UniqueKeyIndex();
      index.setObjId(String.valueOf(nextId));
      index.setId(key);
      userGroup.setId(nextId);
      uniqueKeyIndexWriteDao.save(index);
      writeDao.save(userGroup);
      observable.notifyObserver(ObserverNotification.CREATE_USER_GROUP, userGroup);
    }
    catch (IllegalArgumentException e) {
      String message = ExceptionMessage.CONSTRAINT_VIOLATION_EXCEPTION.name() + "-" +
          UniqueConstrainedField.USER_GROUP_NAME;
      throw new RuntimeException(message, e);
    }
    catch (Exception e) {
      String message = ExceptionMessage.STALE_OBJECT_STATE_EXCEPTION.name() + "-" + UniqueConstrainedField.OTHER;
      throw new RuntimeException(message, e);
    }
  }

  @Override
  public void update(UserGroup userGroup) {
    if (userGroup.getId() == null) {
      throw new IllegalArgumentException("ID of user group not set to be updated!");
    }
    final Date date = new Date();
    userGroup.setLastModifiedDate(date);
    validateUserGroup(userGroup);
    UserGroup oldUserGroup = readDao.getById(userGroup.getId());
    if (oldUserGroup == null) {
      throw new IllegalArgumentException("Trying to update non-existent user group!");
    }
    try {
      if (!userGroup.getName().equals(oldUserGroup.getName())) {
        final UniqueKey oldIndexKey = getUniqueKeyOfIndexForUserGroup(oldUserGroup);
        UniqueKeyIndex index = uniqueKeyIndexReadDao.getById(oldIndexKey);
        if (index == null) {
          index = new UniqueKeyIndex();
          index.setId(oldIndexKey);
          index.setObjId(String.valueOf(userGroup.getId()));
        }
        uniqueKeyIndexWriteDao.delete(index);
        index.setId(getUniqueKeyOfIndexForUserGroup(userGroup));
        uniqueKeyIndexWriteDao.save(index);
      }
      writeDao.update(userGroup);
      observable.notifyObserver(ObserverNotification.UPDATE_USER_GROUP, userGroup);
    }
    catch (IllegalArgumentException e) {
      String message = ExceptionMessage.CONSTRAINT_VIOLATION_EXCEPTION.name() + "-" +
          UniqueConstrainedField.USER_GROUP_NAME;
      throw new RuntimeException(message, e);
    }
    catch (Exception e) {
      String message = ExceptionMessage.STALE_OBJECT_STATE_EXCEPTION.name() + "-" + UniqueConstrainedField.OTHER;
      throw new RuntimeException(message, e);
    }
  }

  @Override
  public void delete(UserGroup userGroup) {
    try {
      writeDao.delete(userGroup);
      observable.notifyObserver(ObserverNotification.DELETE_USER_GROUP, userGroup);
      final UniqueKey indexKey = getUniqueKeyOfIndexForUserGroup(userGroup);
      UniqueKeyIndex index = uniqueKeyIndexReadDao.getById(indexKey);
      if (index != null) {
        uniqueKeyIndexWriteDao.delete(index);
      }
    }
    catch (Exception e) {
      String message = ExceptionMessage.STALE_OBJECT_STATE_EXCEPTION.name() + "-" + UniqueConstrainedField.OTHER;
      throw new RuntimeException(message, e);
    }
  }

  @Override
  public UserGroup getByOrganizationAndUserGroupName(String organizationShortName, String userGroupName) {
    UniqueKey uniqueKey = getUniqueKeyOfIndexForUserGroupName(userGroupName, organizationShortName);
    UniqueKeyIndex index = uniqueKeyIndexReadDao.getById(uniqueKey);
    if (index != null) {
      long userGroupId = NumberUtils.toLong(index.getObjId(), -1l);
      if (userGroupId > -1) {
        return readDao.getById(userGroupId);
      }
    }
    return null;
  }

  @Override
  public Collection<UserGroup> getAllUserGroup() {
    return readDao.getAll();
  }

  @Override
  public Collection<UserGroup> getByOrganizationName(String organizationName) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Collection<UserGroup> getUserGroupsByUser(User user) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void validateUserGroup(UserGroup userGroup) {
    if (StringUtils.isEmpty(userGroup.getName())) {
      throw new RuntimeException(ExceptionMessage.CONSTRAINT_VIOLATION_EXCEPTION.name() + "-" + UniqueConstrainedField.USER_GROUP_NAME.
          name());
    }
    UniqueKey key = getUniqueKeyOfIndexForUserGroup(userGroup);
    UniqueKeyIndex index = uniqueKeyIndexReadDao.getById(key);
    if (index == null) {
      return;
    }
    if (userGroup.getId() != null) {
      if (!String.valueOf(userGroup.getId()).equals(index.getObjId())) {
        throw new RuntimeException(ExceptionMessage.CONSTRAINT_VIOLATION_EXCEPTION.name() + "-" + UniqueConstrainedField.USER_GROUP_NAME.
            name());
      }
    }
    else {
      throw new RuntimeException(ExceptionMessage.CONSTRAINT_VIOLATION_EXCEPTION.name() + "-" + UniqueConstrainedField.USER_GROUP_NAME.
          name());
    }
  }
}