#!/bin/bash
# Oracle Cloud ARM 인스턴스 생성 재시도 스크립트
# 사용법: nohup ./oci-retry-arm.sh > /dev/null 2>&1 &

LOG_FILE=~/.ssh/hwapulgi-arm-retry.log
COMPARTMENT="ocid1.tenancy.oc1..aaaaaaaaoxu5eltkpievsra56fctkaqhattygbko2zekazch4ark23gtvhoq"
AD="vNtG:AP-CHUNCHEON-1-AD-1"
IMAGE="ocid1.image.oc1.ap-chuncheon-1.aaaaaaaauvxjy5tclxvl5nz34arovhumdt37bctdqf23sjuqrlr24vuthkvq"
SUBNET="ocid1.subnet.oc1.ap-chuncheon-1.aaaaaaaaw47kwwt7kubfq7takx74rk32s3eaaentlps6ewxtboxl45ahuruq"
SSH_KEY=~/.ssh/hwapulgi-oci-key.pub

unset OCI_CLI_AUTH
export OCI_CLI_PROFILE=PERMANENT
export PYTHONWARNINGS=ignore

ATTEMPT=0

while true; do
  ATTEMPT=$((ATTEMPT + 1))

  # 2 OCPU + 12GB 시도
  RESULT=$(oci compute instance launch \
    --compartment-id "$COMPARTMENT" \
    --availability-domain "$AD" \
    --shape VM.Standard.A1.Flex \
    --shape-config '{"ocpus":2,"memoryInGBs":12}' \
    --image-id "$IMAGE" \
    --subnet-id "$SUBNET" \
    --assign-public-ip true \
    --display-name hwapulgi-server \
    --ssh-authorized-keys-file "$SSH_KEY" \
    --query 'data.id' --raw-output 2>/tmp/oci-stderr.log)
  ERR=$(cat /tmp/oci-stderr.log | grep -v FutureWarning | grep -v "warnings.warn")

  if echo "$RESULT" | grep -q "ocid1.instance"; then
    INSTANCE_ID=$(echo "$RESULT" | grep -oE 'ocid1\.instance\.[a-zA-Z0-9.-]+')
    echo "[$(date)] SUCCESS: $INSTANCE_ID" >> $LOG_FILE
    osascript -e "display notification \"2 OCPU + 12GB 인스턴스 생성 성공\" with title \"Oracle Cloud\" sound name \"Glass\""
    echo "$INSTANCE_ID" > ~/.ssh/hwapulgi-instance-id.txt
    break
  fi

  SLEEP=60
  if echo "$ERR" | grep -qE "Out of host capacity|connection to endpoint timed out|Could not connect|ServiceUnavailable|InternalError|GatewayTimeout|RequestException|EndOfStream|Read timed out"; then
    # 일시적 에러: 조용히 재시도 (로그는 100회마다만)
    if [ $((ATTEMPT % 100)) -eq 0 ]; then
      echo "[$(date)] Attempt #${ATTEMPT}: still no capacity / transient errors..." >> $LOG_FILE
    fi
  elif echo "$ERR" | grep -q "TooManyRequests"; then
    # Rate limit: 5분 대기
    if [ $((ATTEMPT % 20)) -eq 0 ]; then
      echo "[$(date)] Attempt #${ATTEMPT}: rate limited, backing off 5min..." >> $LOG_FILE
    fi
    SLEEP=300
  elif echo "$ERR" | grep -qE "NotAuthenticated|InvalidParameter|LimitExceeded|Quota|Forbidden"; then
    # 진짜 fatal: 사용자 개입 필요
    echo "[$(date)] FATAL ERROR - Attempt #${ATTEMPT}:" >> $LOG_FILE
    echo "$ERR" >> $LOG_FILE
    osascript -e "display notification \"FATAL: 인증/할당량 문제 발생\" with title \"Oracle Cloud\" sound name \"Basso\""
    sleep 600  # 10분 대기 (계속 시도)
  else
    # 알 수 없는 에러: 자세히 로깅하고 계속
    echo "[$(date)] Attempt #${ATTEMPT}: Unknown error (will retry):" >> $LOG_FILE
    echo "$ERR" | head -20 >> $LOG_FILE
    SLEEP=120
  fi

  sleep $SLEEP
done

echo "[$(date)] Loop ended" >> $LOG_FILE
