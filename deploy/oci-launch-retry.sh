#!/usr/bin/env bash
#
# Keeps trying to create an Oracle Cloud Always Free ARM server (VM.Standard.A1.Flex) until
# capacity is available, then prints its public IP and shows a macOS notification.
#
# Free ARM capacity is often exhausted, especially in single-AD regions; the console just says
# "Out of capacity". This script retries for you, within the Always Free limits only.
#
# Usage:
#   deploy/oci-launch-retry.sh --check   # verify setup, show what it would create, launch nothing
#   deploy/oci-launch-retry.sh           # retry until the server is created (Ctrl+C to stop)
#
# Requirements: the OCI CLI configured in ~/.oci/config (see docs/deploy-oracle.md), jq, a VCN
# with a public subnet, and an SSH public key.
#
# Optional settings (environment variables):
#   INSTANCE_NAME   display name (default: memos)
#   SSH_KEY         public key file (default: ~/.ssh/id_ed25519.pub)
#   SUBNET_ID       subnet to use (default: the first public subnet found)
#   COMPARTMENT_ID  compartment (default: the tenancy's root compartment)
#   SHAPES          OCPU:memory pairs to try, largest first (default: "2:12 1:6"); all must stay
#                   within the Always Free allowance of 2 OCPUs / 12 GB
#   INTERVAL        seconds between attempts (default: 120). Oracle rate-limits launch requests
#                   on free accounts, so one attempt per interval, cycling through SHAPES.
#   OCI_PROFILE     profile in ~/.oci/config (default: DEFAULT)

set -uo pipefail

INSTANCE_NAME="${INSTANCE_NAME:-memos}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519.pub}"
SHAPES="${SHAPES:-2:12 1:6}"
INTERVAL="${INTERVAL:-120}"
OCI_PROFILE="${OCI_PROFILE:-DEFAULT}"
SHAPE="VM.Standard.A1.Flex"
CONFIG_FILE="$HOME/.oci/config"

CHECK_ONLY=false
[[ "${1:-}" == "--check" ]] && CHECK_ONLY=true

log() { printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
die() { log "ERROR: $*"; exit 1; }
# Runs an OCI CLI command, retrying "NotAuthenticated": a newly added API key is accepted only
# intermittently for the first few minutes while it propagates.
# On success prints only the JSON (stdout; CLI warnings go elsewhere), on failure the error text.
oci_() {
    local out err status errfile
    errfile=$(mktemp)
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
        out=$(oci --profile "$OCI_PROFILE" "$@" 2>"$errfile"); status=$?
        err=$(<"$errfile")
        if [[ $status -eq 0 ]]; then
            rm -f "$errfile"; printf '%s' "$out"; return 0
        fi
        if [[ "$err$out" != *NotAuthenticated* ]]; then
            rm -f "$errfile"; printf '%s\n%s' "$err" "$out"; return $status
        fi
        sleep 10
    done
    rm -f "$errfile"; printf '%s\n%s' "$err" "$out"; return $status
}

notify() {
    osascript -e "display notification \"$1\" with title \"Oracle server\" sound name \"Glass\"" >/dev/null 2>&1 || true
}

# --- Preflight -------------------------------------------------------------------------------

command -v oci >/dev/null || die "OCI CLI not found. Install it with: brew install oci-cli"
command -v jq >/dev/null || die "jq not found. Install it with: brew install jq"
[[ -f "$CONFIG_FILE" ]] || die "$CONFIG_FILE not found. Set up an API key first (docs/deploy-oracle.md, step 1b)."
[[ -f "$SSH_KEY" ]] || die "SSH public key $SSH_KEY not found."

for pair in $SHAPES; do
    ocpus="${pair%%:*}"; mem="${pair##*:}"
    if (( ocpus > 2 || mem > 12 )); then
        die "Shape $pair is above the Always Free limit (2 OCPU / 12 GB). Refusing, to avoid charges."
    fi
done

TENANCY_ID=$(awk -v p="[$OCI_PROFILE]" '$0==p{f=1;next} /^\[/{f=0} f && /^tenancy *=/{sub(/^tenancy *= */,"");print;exit}' "$CONFIG_FILE")
[[ -n "$TENANCY_ID" ]] || die "No tenancy= line for profile [$OCI_PROFILE] in $CONFIG_FILE."
COMPARTMENT_ID="${COMPARTMENT_ID:-$TENANCY_ID}"

log "Checking access to Oracle Cloud..."
AD_JSON=$(oci_ iam availability-domain list --compartment-id "$TENANCY_ID") \
    || die "Can't reach Oracle Cloud with ~/.oci/config: $(echo "$AD_JSON" | head -3)"
AD_NAME="${AVAILABILITY_DOMAIN:-$(echo "$AD_JSON" | jq -r '.data[0].name')}"
log "Availability domain: $AD_NAME"

if [[ -z "${SUBNET_ID:-}" ]]; then
    # A failed lookup must not look like "no subnet", so check the call itself first.
    SUBNETS_JSON=$(oci_ network subnet list --compartment-id "$COMPARTMENT_ID" --all) \
        || die "Couldn't list subnets: $(echo "$SUBNETS_JSON" | head -5)"
    SUBNET_ID=$(echo "$SUBNETS_JSON" \
        | jq -r '[.data[]? | select(."prohibit-public-ip-on-vnic" == false and ."lifecycle-state" == "AVAILABLE")][0].id // empty')
fi
[[ -n "$SUBNET_ID" ]] || die "No public subnet found. Create one: Networking > Virtual cloud networks > \
Start VCN Wizard > 'Create VCN with Internet Connectivity', then run this again."
SUBNET_NAME=$(oci_ network subnet get --subnet-id "$SUBNET_ID" | jq -r '.data."display-name"')
log "Subnet: $SUBNET_NAME"

IMAGE_JSON=$(oci_ compute image list --compartment-id "$COMPARTMENT_ID" \
    --operating-system "Canonical Ubuntu" --operating-system-version "24.04" \
    --shape "$SHAPE" --sort-by TIMECREATED --sort-order DESC --limit 1) \
    || die "Couldn't list images: $(echo "$IMAGE_JSON" | head -5)"
IMAGE_ID=$(echo "$IMAGE_JSON" | jq -r '.data[0].id // empty')
[[ -n "$IMAGE_ID" ]] || die "Couldn't find an Ubuntu 24.04 ARM image in this region."
log "Image: $(echo "$IMAGE_JSON" | jq -r '.data[0]."display-name"')"
log "SSH key: $SSH_KEY"
log "Shapes to try in turn: $SHAPES (OCPU:GB), one attempt every ~${INTERVAL}s"

existing_instance() {
    oci_ compute instance list --compartment-id "$COMPARTMENT_ID" --all \
        | jq -r --arg shape "$SHAPE" '[.data[] | select(.shape == $shape and
            (."lifecycle-state" | IN("PROVISIONING","STARTING","RUNNING","STOPPING","STOPPED")))][0].id // empty'
}

public_ip() {
    oci_ compute instance list-vnics --instance-id "$1" | jq -r '.data[0]."public-ip" // empty'
}

report_success() {
    local id="$1" ip=""
    for _ in $(seq 1 30); do
        ip=$(public_ip "$id"); [[ -n "$ip" ]] && break; sleep 10
    done
    log "SUCCESS: server created (instance $id)."
    log "Public IP: ${ip:-not assigned yet - check the console}"
    log "Connect with: ssh ubuntu@${ip:-<public-ip>}"
    notify "Your free ARM server is ready: ${ip:-check the console}"
}

ALREADY=$(existing_instance)
if [[ -n "$ALREADY" ]]; then
    log "You already have an A1 server; not creating another."
    report_success "$ALREADY"
    exit 0
fi

if $CHECK_ONLY; then
    log "Setup looks good. Run without --check to start retrying."
    exit 0
fi

# Keep the Mac awake while this runs (released automatically when the script exits): -i stops
# idle sleep, -s stops system sleep while on power. Closing the lid still sleeps the Mac.
if command -v caffeinate >/dev/null; then
    caffeinate -i -s -w $$ &
fi

# --- Retry loop ------------------------------------------------------------------------------

read -r -a SHAPE_LIST <<< "$SHAPES"
attempt=0
while true; do
        pair="${SHAPE_LIST[$((attempt % ${#SHAPE_LIST[@]}))]}"
        attempt=$((attempt + 1))
        ocpus="${pair%%:*}"; mem="${pair##*:}"
        # Re-check every time, so a server created meanwhile (e.g. in the console) is never duplicated.
        ALREADY=$(existing_instance)
        [[ -n "$ALREADY" ]] && { log "A server now exists; stopping."; report_success "$ALREADY"; exit 0; }

        log "Attempt $attempt: ${ocpus} OCPU / ${mem} GB..."

        # --no-retry: fail fast and let this loop pace the retries (the CLI otherwise backs off for minutes).
        output=$(oci_ --no-retry compute instance launch \
            --availability-domain "$AD_NAME" \
            --compartment-id "$COMPARTMENT_ID" \
            --shape "$SHAPE" \
            --shape-config "{\"ocpus\": $ocpus, \"memoryInGBs\": $mem}" \
            --image-id "$IMAGE_ID" \
            --subnet-id "$SUBNET_ID" \
            --assign-public-ip true \
            --display-name "$INSTANCE_NAME" \
            --ssh-authorized-keys-file "$SSH_KEY")
        status=$?

        if [[ $status -eq 0 ]]; then
            id=$(echo "$output" | jq -r '.data.id // empty' 2>/dev/null)
            report_success "${id:-$(existing_instance)}"
            exit 0
        fi

        code=$(echo "$output" | grep -m1 -oE '"code": "[^"]+"' | cut -d'"' -f4)
        message=$(echo "$output" | grep -m1 -oE '"message": "[^"]*"' | cut -d'"' -f4)

        case "$code:$message" in
            *"Out of host capacity"*|*"Out of capacity"*)
                log "  no capacity yet" ;;
            TooManyRequests:*)
                log "  rate limited by Oracle, slowing down"; sleep $((INTERVAL * 2)) ;;
            InternalError:*|ServiceUnavailable:*)
                log "  Oracle error ($code: ${message:-no message}), will retry" ;;
            LimitExceeded:*|QuotaExceeded:*)
                # Usually means a server already exists (or is being created); never retry past it.
                log "  account limit reached: $message"
                ALREADY=$(existing_instance)
                [[ -n "$ALREADY" ]] && { report_success "$ALREADY"; exit 0; }
                die "Limit exceeded and no server found. Check Governance > Limits in the console." ;;
            NotAuthenticated:*|NotAuthorizedOrNotFound:*|InvalidParameter:*|CannotParseRequest:*)
                die "Request rejected, not a capacity problem ($code): $message" ;;
            *)
                log "  unexpected response, will retry: ${code:-?} ${message:-$(echo "$output" | head -1)}" ;;
        esac
        # Small random jitter so retries don't line up with everyone else's.
        sleep $((INTERVAL + RANDOM % 30))
done
