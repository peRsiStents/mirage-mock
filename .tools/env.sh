#!/usr/bin/env bash
# 本会话构建环境（bash 不持久化 env，每次构建前 source）
export JAVA_HOME="/c/Program Files/Java/jdk1.8.0_271"
export M2_HOME="/d/apache-maven-3.9.16"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
