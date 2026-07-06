// MLCQ cropped selected sample
// Original local source: long_method/08_Long_Method_Oplog.java
// Original target local lines: 1460-1601
// Crop local lines: 1430-1631

    }
    return oplogKeyId;
  }

  /**
   * Given a delta calculate the OplogEntryId for a DEL_ENTRY.
   */
  public long calcDelEntryId(long delta) {
    long oplogKeyId = this.recoverDelEntryId + delta;
    if (logger.isTraceEnabled(LogMarker.PERSIST_RECOVERY_VERBOSE)) {
      logger.trace(LogMarker.PERSIST_RECOVERY_VERBOSE,
          "calcDelEntryId delta={} recoverModEntryId={}  oplogKeyId={}", delta,
          this.recoverModEntryId, oplogKeyId);
    }
    this.recoverDelEntryId = oplogKeyId;
    if (oplogKeyId > this.recoverDelEntryIdHWM) {
      this.recoverDelEntryIdHWM = oplogKeyId; // fixes bug 41340
    }
    return oplogKeyId;
  }

  private boolean crashed;

  boolean isCrashed() {
    return this.crashed;
  }

  /**
   * Return bytes read.
   */
  long recoverDrf(OplogEntryIdSet deletedIds, boolean alreadyRecoveredOnce, boolean latestOplog) {
    File drfFile = this.drf.f;
    if (drfFile == null) {
      this.haveRecoveredDrf = true;
      return 0L;
    }
    lockCompactor();
    try {
      if (this.haveRecoveredDrf && !getHasDeletes())
        return 0L; // do this while holding lock
      if (!this.haveRecoveredDrf) {
        this.haveRecoveredDrf = true;
      }
      logger.info("Recovering {} {} for disk store {}.",
          new Object[] {toString(), drfFile.getAbsolutePath(), getParent().getName()});
      this.recoverDelEntryId = DiskStoreImpl.INVALID_ID;
      boolean readLastRecord = true;
      CountingDataInputStream dis = null;
      try {
        int recordCount = 0;
        boolean foundDiskStoreRecord = false;
        FileInputStream fis = null;
        try {
          fis = new FileInputStream(drfFile);
          dis = new CountingDataInputStream(new BufferedInputStream(fis, 32 * 1024),
              drfFile.length());
          boolean endOfLog = false;
          while (!endOfLog) {
            if (dis.atEndOfFile()) {
              endOfLog = true;
              break;
            }
            readLastRecord = false;
            byte opCode = dis.readByte();
            if (logger.isTraceEnabled(LogMarker.PERSIST_RECOVERY_VERBOSE)) {
              logger.trace(LogMarker.PERSIST_RECOVERY_VERBOSE, "drf byte={} location={}", opCode,
                  Long.toHexString(dis.getCount()));
            }
            switch (opCode) {
              case OPLOG_EOF_ID:
                // we are at the end of the oplog. So we need to back up one byte
                dis.decrementCount();
                endOfLog = true;
                break;
              case OPLOG_DEL_ENTRY_1ID:
              case OPLOG_DEL_ENTRY_2ID:
              case OPLOG_DEL_ENTRY_3ID:
              case OPLOG_DEL_ENTRY_4ID:
              case OPLOG_DEL_ENTRY_5ID:
              case OPLOG_DEL_ENTRY_6ID:
              case OPLOG_DEL_ENTRY_7ID:
              case OPLOG_DEL_ENTRY_8ID:
                readDelEntry(dis, opCode, deletedIds, parent);
                recordCount++;
                break;
              case OPLOG_DISK_STORE_ID:
                readDiskStoreRecord(dis, this.drf.f);
                foundDiskStoreRecord = true;
                recordCount++;
                break;
              case OPLOG_MAGIC_SEQ_ID:
                readOplogMagicSeqRecord(dis, this.drf.f, OPLOG_TYPE.DRF);
                break;
              case OPLOG_GEMFIRE_VERSION:
                readGemfireVersionRecord(dis, this.drf.f);
                recordCount++;
                break;

              case OPLOG_RVV:
                long idx = dis.getCount();
                readRVVRecord(dis, this.drf.f, true, latestOplog);
                recordCount++;
                break;

              default:
                throw new DiskAccessException(
                    String.format("Unknown opCode %s found in disk operation log.",
                        opCode),
                    getParent());
            }
            readLastRecord = true;
            // @todo
            // if (rgn.isDestroyed()) {
            // break;
            // }
          } // while
        } finally {
          if (dis != null) {
            dis.close();
          }
          if (fis != null) {
            fis.close();
          }
        }
        if (!foundDiskStoreRecord && recordCount > 0) {
          throw new DiskAccessException(
              "The oplog file \"" + this.drf.f + "\" does not belong to the init file \""
                  + getParent().getInitFile() + "\". Drf did not contain a disk store id.",
              getParent());
        }
      } catch (EOFException ignore) {
        // ignore since a partial record write can be caused by a crash
      } catch (IOException ex) {
        getParent().getCancelCriterion().checkCancelInProgress(ex);
        throw new DiskAccessException(
            String.format("Failed to read file during recovery from %s",
                drfFile.getPath()),
            ex, getParent());
      } catch (CancelException e) {
        if (logger.isDebugEnabled()) {
          logger.debug("Oplog::readOplog:Error in recovery as Cache was closed", e);
        }
      } catch (RegionDestroyedException e) {
        if (logger.isDebugEnabled()) {
          logger.debug("Oplog::readOplog:Error in recovery as Region was destroyed", e);
        }
      }
      // Add the Oplog size to the Directory Holder which owns this oplog,
      // so that available space is correctly calculated & stats updated.
      long byteCount = 0;
      if (!readLastRecord) {
        // this means that there was a crash
        // and hence we should not continue to read
        // the next oplog
        this.crashed = true;
        if (dis != null) {
          byteCount = dis.getFileLength();
        }
      } else {
        if (dis != null) {
          byteCount = dis.getCount();
        }
      }
      if (!alreadyRecoveredOnce) {
        setRecoveredDrfSize(byteCount);
        this.dirHolder.incrementTotalOplogSize(byteCount);
      }
      return byteCount;
    } finally {
      unlockCompactor();
    }
  }

  /**
   * This map is used during recovery to keep track of what entries were recovered. Its keys are the
   * oplogEntryId; its values are the actual logical keys that end up in the Region's keys. It used
   * to be a local variable in basicInitializeOwner but now that it needs to live longer than that
   * method I made it an instance variable It is now only alive during recoverRegionsThatAreReady so
   * it could once again be passed down into each oplog.
   * <p>
   * If offlineCompaction the value in this map will have the key bytes, values bytes, user bits,
   * etc (any info we need to copy forward).
   */
  private OplogEntryIdMap kvMap;

  private OplogEntryIdMap getRecoveryMap() {
    return this.kvMap;
  }

  /**
   * This map is used during recover to keep track of keys that are skipped. Later modify records in
   * the same oplog may use this map to retrieve the correct key.
   */
  private OplogEntryIdMap skippedKeyBytes;

  private boolean readKrf(OplogEntryIdSet deletedIds, boolean recoverValues,
      boolean recoverValuesSync, Set<Oplog> oplogsNeedingValueRecovery, boolean latestOplog) {
    File f = new File(this.diskFile.getPath() + KRF_FILE_EXT);
    if (!f.exists()) {
      return false;
    }

