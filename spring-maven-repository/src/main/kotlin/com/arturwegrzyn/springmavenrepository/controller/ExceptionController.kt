package com.arturwegrzyn.springmavenrepository.controller

import com.arturwegrzyn.springmavenrepository.storage.StorageException
import com.arturwegrzyn.springmavenrepository.storage.StorageFileNotFoundWithFileNameException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.view.RedirectView
import java.util.*
import java.util.stream.Collectors

@ControllerAdvice
class ExceptionController @Autowired constructor(private val env: Environment) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    fun exception(e: Exception): ResponseEntity<Any> {
        log.error(e.toString())
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.message)
    }

    @ExceptionHandler(StorageFileNotFoundWithFileNameException::class)
    fun storageFileNotFoundWithFileNameException(e: StorageFileNotFoundWithFileNameException): Any {
        log.debug(e.toString())
        val mavenTargetRepositoryUrl = env.getProperty("maven.redirectUrl")
        if (Objects.nonNull(mavenTargetRepositoryUrl)) {
            val isMultiRepo = env.getProperty("maven.multiRepo")?.toBoolean() ?: false
            val filePath = if (isMultiRepo)
                e.filename.split("/").stream()
                        .skip(1)
                        .collect(Collectors.joining("/"))
            else
                e.filename
            val targetAddress = "$mavenTargetRepositoryUrl/$filePath"
            log.debug("Redirection request to address $targetAddress")
            return RedirectView(targetAddress);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.message)
        }
    }

    @ExceptionHandler(StorageException::class)
    fun storageException(e: StorageException): ResponseEntity<Any> {
        log.error(e.toString())
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.message)
    }
}