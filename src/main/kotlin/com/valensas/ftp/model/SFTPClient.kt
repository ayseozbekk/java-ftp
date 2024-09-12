package com.valensas.ftp.model

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelSftp.LsEntry
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.apache.commons.net.ftp.FTPClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.UUID

class SFTPClient : FTPClient() {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private lateinit var session: Session
    private lateinit var channel: ChannelSftp

    fun connect(connectionModel: ConnectionModel) {
        try {
            if (connectionModel.password == null &&
                connectionModel.privateKey == null
            ) {
                throw Exception("Both password and private key can not be null")
            }
            val jSch = JSch()

            connectionModel.privateKey?.let {
                jSch.addIdentity(UUID.randomUUID().toString(), it.toByteArray(), null, null)
            }
            session = jSch.getSession(connectionModel.username, connectionModel.host, connectionModel.port)
            connectionModel.connectionTimeout?.let {
                session.timeout = it
            }
            connectionModel.password?.let {
                session.setPassword(it)
            }
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect()
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
        } catch (e: Exception) {
            logger.error("An error occurred while connecting to SFTP Server", e)
            throw e
        }
    }

    override fun retrieveFileStream(remoteFileName: String): InputStream = channel.get(remoteFileName)

    override fun deleteFile(remoteFileName: String): Boolean =
        try {
            channel.rm(remoteFileName)
            true
        } catch (e: Exception) {
            logger.error("Can not deleted $remoteFileName.", e)
            throw e
        }

    fun listFilesAtPath(pathname: String): List<LsEntry> {
        val fileVector = channel.ls(pathname)
        return fileVector
            .map {
                it as LsEntry
            }.filter {
                !it.attrs.isDir
            }
    }

    fun uploadFile(
        inputStream: InputStream,
        sftpPath: String,
    ) {
        try {
            channel.put(inputStream, sftpPath)
        } catch (e: Exception) {
            logger.error("Error happened while uploading file", e)
            throw e
        }
    }

    fun directories(path: String = "."): List<LsEntry> {
        val fileVector = channel.ls(path)
        return fileVector
            .map {
                it as LsEntry
            }.filter {
                it.attrs.isDir
            }
    }

    fun setTimeout(timeout: Int) {
        if (::session.isInitialized) {
            session.timeout = timeout
        }
    }

    override fun isConnected(): Boolean = channel.isConnected

    override fun disconnect() {
        if (::session.isInitialized) {
            session.disconnect()
        }
        if (::channel.isInitialized) {
            channel.disconnect()
        }
    }
}
